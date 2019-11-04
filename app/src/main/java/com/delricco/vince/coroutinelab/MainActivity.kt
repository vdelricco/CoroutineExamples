package com.delricco.vince.coroutinelab

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        button.setOnClickListener { getImage() }
    }

    // When used as a "parent", a normal Job's behavior is to cancel itself and all of it's
    // children when one of it's children fails. We'll use a SupervisorJob, whose behavior
    // is to allow it's children to fail/cancel independently of itself and it's other children
    private val myCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    // This is the same as above -> private val myCoroutineScope = MainScope()

    // We only want to fire one of these pieces of work off at a time. To achieve this,
    // we'll keep a local variable we can cancel on, then recreate.
    // Remember! We cannot simply reuse the same job because it will be in one of it's
    // final states (Completed/Cancelled).
    private var imageLoadingJob: Job? = null
    private fun getImage() {
        imageLoadingJob?.cancel()
        // We're entering the land of suspension. This launch method will
        // return immediately with a reference to our newly created "coroutine" (Job)
        imageLoadingJob = myCoroutineScope.launch(CoroutineName("getImage")) {
            // Inside the block, we are free to call suspension functions
            coroutineContext.printInfo()
            // Switch the image to pretend we're loading
            imageView.setImageDrawable(getDrawable(R.drawable.ic_launcher_background))

            // Use the deferred object to wait for our image
            imageView.setImageDrawable(fetchImageAsync().await())

            // OR
            // Start the suspend function on the IO dispatcher. This does the same thing
            // as above, but is a different design decision.
//            val deferredImage = async(Dispatchers.IO + CoroutineName("fetchImage")) {
//                fetchImage()
//            }
//            imageView.setImageDrawable(deferredImage.await())
        }
    }

    // Don't do this! This needlessly creates a
    // new scope and replaces the calling Scope's Job with it's own
    private suspend fun fetchImageAsyncWRONG(): Deferred<Drawable> = coroutineScope {
        async(Dispatchers.IO) {
            coroutineContext.printInfo()
            delay(1500)
            resources.getDrawable(R.drawable.bear1, theme)
        }
    }

    // Instead, if you intend to launch another coroutine
    // from a function, create an extension of CoroutineScope
    // to achieve implicit inheritance
    private fun CoroutineScope.fetchImageAsync(): Deferred<Drawable> {
        // Call async (to return a Deferred) using the contextual CoroutineScope,
        // but we'll add the ContinuationInterceptor we want to use for this work.
        return this.async(Dispatchers.IO + CoroutineName("fetchImageAsync")) {
            coroutineContext.printInfo()
            delay(1500)
            resources.getDrawable(R.drawable.bear1, theme)
        }
    }

    // Suspending functions should be non-blocking and
    // should not have side-effects of launching any concurrent work.
    private suspend fun fetchImage(): Drawable {
        coroutineContext.printInfo()
        delay(1500)
        return resources.getDrawable(R.drawable.bear1, theme)
    }

    private fun CoroutineContext.printInfo() {
        println("Name: ${get(CoroutineName)}, Dispatcher: ${get(ContinuationInterceptor)}]")
    }

    // NOT THE CORRECT CALLBACK FOR CANCELLATION
    // This would cause the Scope to have a cancelled parent job
    // in the case that we re-enter the app through onResume.
    override fun onPause() {
//        myCoroutineScope.cancel()
        super.onPause()
    }

    // This is where you would want to cancel your scope. The cancellation
    // propagates to all child job unless they've been marked NonCancellable
    override fun onDestroy() {
        myCoroutineScope.cancel()
        super.onDestroy()
    }
}