package com.vibe.app.feature.agent.loop.compaction

import com.vibe.app.data.model.ClientType


data class ProviderContextBudget(
    val maxTokens: Int,
    val recentTurns: Int,
) {

    companion object {


        fun forProvider(
            clientType: ClientType
        ): ProviderContextBudget = when (clientType) {


            ClientType.OPEN_ROUTER ->
                ProviderContextBudget(
                    maxTokens = 60_000,
                    recentTurns = 5
                )


            ClientType.CUSTOM ->
                ProviderContextBudget(
                    maxTokens = 60_000,
                    recentTurns = 5
                )

        }

    }

}
