package com.example.weatherrecommender.di

import com.example.weatherrecommender.domain.usecase.scorer.ActivityScorer
import com.example.weatherrecommender.domain.usecase.scorer.SkiScorer
import com.example.weatherrecommender.domain.usecase.scorer.SurfScorer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import com.example.weatherrecommender.domain.usecase.scorer.IndoorSightseeingScorer
import com.example.weatherrecommender.domain.usecase.scorer.OutdoorSightseeingScorer

/**
 * Hilt module for providing domain-layer dependencies, specifically strategy pattern implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    /**
     * Provides the set of [ActivityScorer] strategies used to rank weather activities.
     */
    @Provides
    @ElementsIntoSet
    fun provideActivityScorers(): Set<ActivityScorer> {
        return setOf(
            SurfScorer(),
            SkiScorer(),
            OutdoorSightseeingScorer(),
            IndoorSightseeingScorer()
        )
    }
}
