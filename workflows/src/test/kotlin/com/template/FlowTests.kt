package com.template

import com.r3.corda.lib.accounts.workflows.services.AccountService
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import com.template.flows.CreateNewAccount
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Future;
import net.corda.core.node.services.vault.QueryCriteria
import com.template.flows.SendMoney
import com.template.flows.ShareAccountTo
import com.template.states.BankAccount
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.WaitForStateConsumption.Companion.logger
import net.corda.testing.common.internal.testNetworkParameters
import java.util.*
import java.util.concurrent.ExecutionException


class FlowTests {
    private lateinit var network: MockNetwork
    private lateinit var a: StartedMockNode
    private lateinit var b: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("com.template.contracts"),
            TestCordapp.findCordapp("com.template.states"),
            TestCordapp.findCordapp("com.template.flows"),
            TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
            TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows")
        ), networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
            notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB")))
        ))
        a = network.createPartyNode()
        b = network.createPartyNode()
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    fun AccountCreation() {
        val createAcct = CreateNewAccount("TestAccountA")
        val future: Future<String> = a.startFlow(createAcct)
        network.runNetwork()
        val accountService: AccountService = a.services.cordaService(KeyManagementBackedAccountService::class.java)
        val myAccount = accountService.accountInfo("TestAccountA")
        logger.info(" myAccount :"+myAccount)
        assert(myAccount.size != 0)
    }

    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    fun MoneySendFlowTest() {
        val createAcct = CreateNewAccount("TestAccountA")
        val future: Future<String> = a.startFlow(createAcct)
        network.runNetwork()
        val shareAToB = ShareAccountTo("TestAccountA", b.info.legalIdentities[0])
        val future2: Future<String> = a.startFlow(shareAToB)
        network.runNetwork()
        val createAcct2 = CreateNewAccount("TestAccountB")
        val future3: Future<String> = b.startFlow(createAcct2)
        network.runNetwork()
        val shareBToA = ShareAccountTo("TestAccountB", a.info.legalIdentities[0])
        val future4: Future<String> = b.startFlow(shareBToA)
        network.runNetwork()
        val sendMoneyflow = SendMoney("TestAccountA", "TestAccountB", 2000)
        val future5: Future<String> = a.startFlow(sendMoneyflow)
        network.runNetwork()

        //retrieve
        val accountService: AccountService = b.services.cordaService(KeyManagementBackedAccountService::class.java)
        val (_, _, identifier) = accountService.accountInfo("TestAccountB")[0].state.data
        logger.info(" identifier : "+identifier)
        val criteria = QueryCriteria.VaultQueryCriteria()
            .withExternalIds(Arrays.asList(identifier.id))

        val storedState = b.services.vaultService.queryBy(BankAccount::class.java, criteria).states[0].state.data
        logger.info(" storedState : "+storedState.toString())
        assert(storedState.amount == 2000)
    }


}