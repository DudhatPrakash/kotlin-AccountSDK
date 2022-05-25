package com.template.states

import com.template.contracts.BankAccountContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty

@BelongsToContract(BankAccountContract::class)
class BankAccount (val amount: Int,
                   val sender: AnonymousParty,
                   val recipient: AnonymousParty)
    : ContractState{

    override val participants: List<AbstractParty> get() = listOfNotNull(recipient,sender).map { it }
    override fun toString(): String {
        return "BankAccount(amount=$amount, sender=$sender, recipient=$recipient, participants=$participants)"
    }
}