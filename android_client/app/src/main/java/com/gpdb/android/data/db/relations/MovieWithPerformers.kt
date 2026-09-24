package com.gpdb.android.data.db.relations

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.gpdb.android.data.db.entities.MovieEntity
import com.gpdb.android.data.db.entities.MoviePerformerEntity
import com.gpdb.android.data.db.entities.PerformerEntity

data class MovieWithPerformers(
    @Embedded
    val movie: MovieEntity,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = MoviePerformerEntity::class,
            parentColumn = "movie_id",
            entityColumn = "performer_id"
        )
    )
    val performers: List<PerformerEntity>
)
