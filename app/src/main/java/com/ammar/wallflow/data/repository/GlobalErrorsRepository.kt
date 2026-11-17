package com.ammar.wallflow.data.repository

import com.ammar.wallflow.model.Source
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Singleton
class GlobalErrorsRepository @Inject constructor() {
    private val _errors = MutableStateFlow<List<GlobalError>>(emptyList())
    val errors: StateFlow<List<GlobalError>> = _errors

    fun addError(error: GlobalError, replace: Boolean = false) = _errors.update {
        var errorList = it
        if (replace) {
            errorList = errorList.filter { e -> e.key != error.key }
        }
        errorList + error
    }

    /** Removes the rate-limit error for one source only, so other sources keep theirs. */
    fun removeRateLimitError(source: Source) = _errors.update {
        it.filterNot { e -> e is RateLimitError && e.source == source }
    }

    fun removeError(error: GlobalError) = _errors.update {
        it.filter { e -> e != error }
    }

    fun removeErrorByType(vararg type: GlobalErrorType) = _errors.update {
        it.filter { e -> e.type !in type }
    }

    enum class GlobalErrorType {
        WALLHAVEN_UNAUTHORISED,
        RATE_LIMIT,
        REDDIT_UNAUTHORISED,
    }

    sealed class GlobalError {
        abstract val type: GlobalErrorType

        /** Errors with the same key replace each other. Defaults to the type. */
        open val key: Any get() = type
    }

    class WallHavenUnauthorisedError : GlobalError() {
        override val type: GlobalErrorType = GlobalErrorType.WALLHAVEN_UNAUTHORISED
    }

    class RedditUnauthorisedError : GlobalError() {
        override val type: GlobalErrorType = GlobalErrorType.REDDIT_UNAUTHORISED
    }

    class RateLimitError(val source: Source) : GlobalError() {
        override val type: GlobalErrorType = GlobalErrorType.RATE_LIMIT
        override val key: Any get() = Pair(type, source)
    }

}
