package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.template.contracts.BankAccountContract
import com.template.states.BankAccount
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@StartableByRPC
@StartableByService
@InitiatingFlow
class SendMoney (val fromAcc: String,
                val toAcc: String,
                val amount: Int)
    :FlowLogic<String>(){

    @Suspendable
    override fun call(): String {
        val myAccount= accountService.accountInfo(fromAcc).single().state.data
        logger.info(" myAccount :"+myAccount)
        val myKey= subFlow(NewKeyForAccount(myAccount.identifier.id)).owningKey
        logger.info(" myKey :"+myKey)

        val toAcc=accountService.accountInfo(toAcc).single().state.data
        logger.info("toAcc : "+toAcc)
        val toAccKey=subFlow(RequestKeyForAccount(toAcc))
        logger.info("toAccKey : "+toAccKey)

        val output= BankAccount(amount, AnonymousParty(myKey),toAccKey)
        val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB"))

        val tranBuilder= TransactionBuilder(notary)
            .addOutputState(output)
            .addCommand(BankAccountContract.Commands.Send(),listOf(toAccKey.owningKey,myKey))

        val localTx=serviceHub.signInitialTransaction(tranBuilder,listOf(ourIdentity.owningKey,myKey))
        val sessionFlow= initiateFlow(toAcc.host)
        val stx=subFlow(CollectSignatureFlow(localTx,sessionFlow,toAccKey.owningKey))
        val signedByCounterParty = localTx.withAdditionalSignatures(stx)
        subFlow(FinalityFlow(signedByCounterParty, listOf(sessionFlow).filter { it.counterparty != ourIdentity }))

        return "Money send to " + toAcc.host.name.organisation
    }

}


@InitiatedBy(SendMoney::class)
class SendMoneyResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>(){
    @Suspendable
    override fun call() {
        subFlow(object : SignTransactionFlow(counterpartySession) {
            @Throws(FlowException::class)
            override fun checkTransaction(stx: SignedTransaction) {
                // Custom Logic to validate transaction.
            }
        })
        subFlow(ReceiveFinalityFlow(counterpartySession))
    }

}