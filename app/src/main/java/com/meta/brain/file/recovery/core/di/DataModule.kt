package com.meta.brain.file.recovery.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.meta.brain.file.recovery.data.datasource.FileSystemDataSource
import com.meta.brain.file.recovery.data.datasource.MediaStoreDataSource
import com.meta.brain.file.recovery.data.repository.MediaRepository
import com.meta.brain.file.recovery.data.repository.MetricsRepository
import com.meta.brain.file.recovery.data.scanner.DeletedFileScanner
import com.meta.brain.file.recovery.data.scanner.HiddenFileScanner
import com.meta.brain.file.recovery.data.scanner.TrashScanner
import com.meta.brain.file.recovery.data.scanner.UnindexedFileScanner
import com.meta.brain.file.recovery.data.util.MediaEntryFactory
import com.meta.brain.file.recovery.data.util.MimeTypeUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "metrics")

/**
 * Data module providing all data layer dependencies
 * Clean Architecture: Provides repositories, data sources, scanners, and utilities
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    // DataStore and basic repositories
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    @Provides
    @Singleton
    fun provideMetricsRepository(dataStore: DataStore<Preferences>): MetricsRepository {
        return MetricsRepository(dataStore)
    }

    // Utilities
    @Provides
    @Singleton
    fun provideMimeTypeUtils(): MimeTypeUtils {
        return MimeTypeUtils()
    }

    @Provides
    @Singleton
    fun provideMediaEntryFactory(mimeTypeUtils: MimeTypeUtils): MediaEntryFactory {
        return MediaEntryFactory(mimeTypeUtils)
    }

    // Data sources
    @Provides
    @Singleton
    fun provideMediaStoreDataSource(
        @ApplicationContext context: Context,
        mediaEntryFactory: MediaEntryFactory
    ): MediaStoreDataSource {
        return MediaStoreDataSource(context, mediaEntryFactory)
    }

    @Provides
    @Singleton
    fun provideFileSystemDataSource(mediaEntryFactory: MediaEntryFactory): FileSystemDataSource {
        return FileSystemDataSource(mediaEntryFactory)
    }

    // Scanners
    @Provides
    @Singleton
    fun provideHiddenFileScanner(fileSystemDataSource: FileSystemDataSource): HiddenFileScanner {
        return HiddenFileScanner(fileSystemDataSource)
    }

    @Provides
    @Singleton
    fun provideTrashScanner(fileSystemDataSource: FileSystemDataSource): TrashScanner {
        return TrashScanner(fileSystemDataSource)
    }

    @Provides
    @Singleton
    fun provideDeletedFileScanner(
        @ApplicationContext context: Context,
        fileSystemDataSource: FileSystemDataSource,
        mediaEntryFactory: MediaEntryFactory
    ): DeletedFileScanner {
        return DeletedFileScanner(context, fileSystemDataSource, mediaEntryFactory)
    }

    @Provides
    @Singleton
    fun provideUnindexedFileScanner(
        fileSystemDataSource: FileSystemDataSource,
        mediaStoreDataSource: MediaStoreDataSource
    ): UnindexedFileScanner {
        return UnindexedFileScanner(fileSystemDataSource, mediaStoreDataSource)
    }

    // Main repository (orchestrator)
    @Provides
    @Singleton
    fun provideMediaRepository(
        mediaStoreDataSource: MediaStoreDataSource,
        hiddenFileScanner: HiddenFileScanner,
        trashScanner: TrashScanner,
        deletedFileScanner: DeletedFileScanner,
        unindexedFileScanner: UnindexedFileScanner
    ): MediaRepository {
        return MediaRepository(
            mediaStoreDataSource,
            hiddenFileScanner,
            trashScanner,
            deletedFileScanner,
            unindexedFileScanner
        )
    }
}
