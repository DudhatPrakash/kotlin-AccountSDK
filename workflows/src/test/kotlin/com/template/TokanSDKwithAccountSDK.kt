package com.template

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.OurAccounts
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.*
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.workflows.flows.rpc.ConfidentialRedeemFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.FungibleState
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.TestCordapp
import org.junit.Test
import kotlin.test.assertEquals

class TokanSDKwithAccountSDK {

    companion object {
        private val log = contextLogger()
    }

    private val partyA = NodeParameters(
        providedName = CordaX500Name("PartyA", "London", "GB"),
        additionalCordapps = listOf()
    )

    private val partyB = NodeParameters(
        providedName = CordaX500Name("PartyB", "London", "GB"),
        additionalCordapps = listOf()
    )

    private val issuer = NodeParameters(
        providedName = CordaX500Name("Issuer", "London", "GB"),
        additionalCordapps = listOf()
    )

    private val nodeParams = listOf(partyA, partyB, issuer)

    private val defaultCorDapps = listOf(
        TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
        TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
        TestCordapp.findCordapp("com.r3.corda.lib.tokens.money"),
        TestCordapp.findCordapp("com.r3.corda.lib.tokens.selection"),
        TestCordapp.findCordapp("com.r3.corda.lib.ci"),
        TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
        TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows"),
        TestCordapp.findCordapp("com.template.contracts"),
        TestCordapp.findCordapp("com.template.flows")
    )

    private val driverParameters = DriverParameters(
        startNodesInProcess = false,
        cordappsForAllNodes = defaultCorDapps,
        networkParameters = testNetworkParameters(notaries = emptyList(), minimumPlatformVersion = 4)
    )

    fun NodeHandle.legalIdentity() = nodeInfo.legalIdentities.single()

    @Test
    fun `node test`() {
        driver(driverParameters) {
            log.info("All nodes started up.")
            val (A, I) = nodeParams.map { params -> startNode(params) }.transpose().getOrThrow()

            log.info("Creating two accounts on node A.")
            val createAccountsOnA = listOf(
                A.rpc.startFlow(::CreateAccount, "PartyA - A1").returnValue,
                A.rpc.startFlow(::CreateAccount, "PartyA - A2").returnValue
            ).transpose().getOrThrow()

            log.info("Sharing account info from node A to Issuer.")
            val aAccountsQuery = A.rpc.startFlow(::OurAccounts).returnValue.getOrThrow()
            val a1Account = aAccountsQuery.single { it.state.data.name == "PartyA - A1" }
            A.rpc.startFlow(::ShareAccountInfo, a1Account, listOf(I.legalIdentity())).returnValue.getOrThrow()

            log.info("Issuer requesting new key for account on node A.")
            val rogerAnonymousParty = I.rpc.startFlow(::RequestKeyForAccount, a1Account.state.data).returnValue.getOrThrow()

            // Issue tokens.
            log.info("Issuer issuing 100 GBP to account on node A.")
            val tokens = 100 of GBP issuedBy I.legalIdentity() heldBy rogerAnonymousParty
            val issuanceResult = I.rpc.startFlow(
                ::IssueTokens,
                listOf(tokens),
                emptyList()
            ).returnValue.getOrThrow()

            // Move tokens.
            log.info("Node A moving tokens between accounts.")
            val a2Account = aAccountsQuery.single { it.state.data.name == "PartyA - A2" }
            val kasiaAnonymousParty = A.rpc.startFlow(::RequestKeyForAccount, a2Account.state.data).returnValue.getOrThrow()
            val newAnonymousParty = A.rpc.startFlow(::RequestKeyForAccount, a1Account.state.data).returnValue.getOrThrow()

            val moveTokensTransaction = A.rpc.startFlowDynamic(
                MoveFungibleTokens::class.java,
                PartyAndAmount(kasiaAnonymousParty, 50.GBP),
                emptyList<Party>(),
                null,
                newAnonymousParty as AbstractParty
            ).returnValue.getOrThrow()


            // Redeem tokens.
            val kasiaChangeKey = A.rpc.startFlow(::RequestKeyForAccount, a2Account.state.data).returnValue.getOrThrow()
            log.info("Redeeming tokens from PartyA - A2's account.")
            val redeemTokensTransaction = A.rpc.startFlowDynamic(
                ConfidentialRedeemFungibleTokens::class.java,
                30.GBP,
                I.legalIdentity(),
                emptyList<Party>(),
                QueryCriteria.VaultQueryCriteria(externalIds = listOf(a2Account.state.data.identifier.id)),
                kasiaChangeKey
            )
            log.info(" redeemTokensTransaction : "+redeemTokensTransaction.returnValue.getOrThrow().tx.toString())

            val kasiaQueryTwo = A.rpc.vaultQueryByCriteria(
                QueryCriteria.VaultQueryCriteria(externalIds = listOf(a2Account.state.data.identifier.id)),
                FungibleToken::class.java
            )
            assertEquals(20.GBP, (kasiaQueryTwo.states).sumTokenStateAndRefs().withoutIssuer())
        }
    }
}

fun CordaRPCOps.watchForTransaction(tx: SignedTransaction): CordaFuture<SignedTransaction> {
    val (snapshot, feed) = internalVerifiedTransactionsFeed()
    return if (tx in snapshot) {
        doneFuture(tx)
    } else {
        feed.filter { it.id == tx.id }.toFuture()
    }
}

fun CordaRPCOps.watchForTransaction(txId: SecureHash): CordaFuture<SignedTransaction> {
    val (snapshot, feed) = internalVerifiedTransactionsFeed()
    return if (txId in snapshot.map { it.id }) {
        doneFuture(snapshot.single { txId == it.id })
    } else {
        feed.filter { it.id == txId }.toFuture()
    }
}