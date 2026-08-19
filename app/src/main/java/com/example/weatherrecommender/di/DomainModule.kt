package com.example.weatherrecommender.di

import com.example.weatherrecommender.domain.usecase.scorer.ActivityScorer
import com.example.weatherrecommender.domain.usecase.scorer.IndoorSightseeingScorer
import com.example.weatherrecommender.domain.usecase.scorer.OutdoorSightseeingScorer
import com.example.weatherrecommender.domain.usecase.scorer.SkiScorer
import com.example.weatherrecommender.domain.usecase.scorer.SurfScorer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Binds each [ActivityScorer] into the set consumed by [com.example.weatherrecommender.domain.usecase.GetRankedActivitiesUseCase].
 *
 * A new activity is one class plus one `@Binds @IntoSet` method — the use case is not edited.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Binds
    @IntoSet
    abstract fun bindSurfScorer(impl: SurfScorer): ActivityScorer

    @Binds
    @IntoSet
    abstract fun bindSkiScorer(impl: SkiScorer): ActivityScorer

    @Binds
    @IntoSet
    abstract fun bindOutdoorSightseeingScorer(impl: OutdoorSightseeingScorer): ActivityScorer

    @Binds
    @IntoSet
    abstract fun bindIndoorSightseeingScorer(impl: IndoorSightseeingScorer): ActivityScorer
}
