package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.workflows.accountService
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.utilities.getOrThrow

@StartableByRPC
@StartableByService
@InitiatingFlow
class CreateNewAccount(val accountName: String): FlowLogic<String>() {

    @Suspendable
    override fun call(): String {
        val newAcc=accountService.createAccount(name=accountName).toCompletableFuture().getOrThrow()
        val acc=newAcc.state.data
        return ""+acc + " team's account was created. UUID is : " + acc.identifier
    }
}