package com.delricco.vince.coroutinelab

import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.*
import org.junit.Test
import java.lang.IllegalArgumentException
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@ExperimentalCoroutinesApi
class CoroutineUnitTest {
    @Test
    fun `CoroutineContext is a heterogeneous map with type-safe fetching`() {
        // Empty CoroutineContext is the simplest implementation
        var testContext: CoroutineContext = EmptyCoroutineContext
        assertSame(testContext[ContinuationInterceptor], null)

        // You can add other CoroutineContext.Element's with the + operator
        testContext += Dispatchers.IO
        assertSame(testContext[ContinuationInterceptor], Dispatchers.IO)

        // Elements with the same key will be overridden
        testContext += Dispatchers.Main
        assertSame(testContext[ContinuationInterceptor], Dispatchers.Main)

        // You can also remove keys in a similar way
        testContext = testContext.minusKey(ContinuationInterceptor)
        assertSame(testContext[ContinuationInterceptor], null)

        // A single CoroutineContext.Element is a singleton CoroutineContext when used by itself
        assertTrue(testContext is EmptyCoroutineContext)
        testContext += Dispatchers.IO
        assertTrue(testContext is ContinuationInterceptor)
        testContext += CoroutineName("My Coroutine")
        assertFalse(testContext is ContinuationInterceptor)
        assertTrue(testContext[CoroutineName]?.name == "My Coroutine")
    }

    @Test
    fun `CoroutineScopes have a CoroutineContext and use it to create jobs`() {
        val testScope = CoroutineScope(EmptyCoroutineContext)

        // Create a job from the scope
        val testJob = testScope.launch { }

        // Jobs created from a scope whose context is lacking a ContinuationInterceptor will
        // be assigned the Default one.
        assertSame(testJob.context[ContinuationInterceptor], Dispatchers.Default)
        // A Job's Context contains a Job element which points back to the Job itself
        assertSame(testJob.context[Job], testJob)
    }

    @Test
    fun `Jobs are cancellable & completable`() = runBlockingTest {
        val testCoroutineScope = TestCoroutineScope()
        // This job will be "active" until this delay is fulfilled
        val testJob = testCoroutineScope.launch { delay(Long.MAX_VALUE) }

        // Unless we cancel it!
        testJob.cancel()

        // Assert what we assume is true
        assertTrue(testJob.isCancelled)

        // Since this block is empty, this Job will complete immediately, rendering a cancel useless
        val testJob2 = testCoroutineScope.launch {}

        testJob2.cancel()

        // The Job is in the completed state, not cancelled (the two final states)
        assertTrue(testJob2.isCompleted)
        assertFalse(testJob2.isCancelled)
    }

    @Test
    fun `Jobs created by a scope have an implicit parent-child relationship`() {
        val parentJob = Job()
        // TestCoroutineScope doesn't create a backing Job like CoroutineScope does
        val testCoroutineScope = TestCoroutineScope(parentJob)
        val testJob = testCoroutineScope.launch { delay(Long.MAX_VALUE) }

        // Assert the job is running like we think it is
        assertTrue(testJob.isActive)

        parentJob.cancelChildren()

        assertTrue(testJob.isCancelled)
    }

    @Test
    fun `Cancelled parents cannot be used to start new Jobs`() {
        val parentJob = Job()
        val testCoroutineScope = TestCoroutineScope(parentJob)

        parentJob.cancel()

        val job = testCoroutineScope.launch { delay(Long.MAX_VALUE) }

        assertFalse(job.isActive)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `A parent job cancels all its children if one fails`() {
        val parentJob = Job()
        val testCoroutineScope = TestCoroutineScope(parentJob)

        val job1 = testCoroutineScope.launch { delay(Long.MAX_VALUE) }

        // Creates another job that we don't use the reference to
        testCoroutineScope.launch {
            throw IllegalArgumentException("WAHHH")
        }

        assertTrue(job1.isCancelled)
    }

    @Test
    fun `A parent supervisor job cancels all its children if one fails`() {
        val parentJob = SupervisorJob()
        val testCoroutineScope = TestCoroutineScope(parentJob)

        val job1 = testCoroutineScope.launch { delay(Long.MAX_VALUE) }

        // Creates another job that we don't use the reference to
        testCoroutineScope.launch {
            throw IllegalArgumentException("FAIL")
        }

        assertTrue(job1.isActive)
    }

    @Test
    fun `Context is passed between scopes`() {
        val coroutineName = CoroutineName("Parent")

        val testScope = CoroutineScope(coroutineName)

        val job1 = testScope.launch {}
        val job2 = testScope.launch(CoroutineName("Child")) { }

        assertTrue(testScope.coroutineContext[CoroutineName]?.name == "Parent")
        assertTrue(job1.context[CoroutineName]?.name == "Parent")
        assertTrue(job2.context[CoroutineName]?.name == "Child")
    }

    private val Job.context get() = (this as CoroutineScope).coroutineContext
}