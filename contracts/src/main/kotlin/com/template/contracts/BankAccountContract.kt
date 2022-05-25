package com.template.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class BankAccountContract : Contract{
    override fun verify(tx: LedgerTransaction) {

    }
    interface Commands : CommandData {
        class Create : Commands
        class Send : Commands
    }
}