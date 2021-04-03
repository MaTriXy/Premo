/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2021 Dmitriy Gorbunov (dmitriy.goto@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.dmdev.premo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.dmdev.premo.navigation.NavigationMessage
import me.dmdev.premo.navigation.PmFactory
import me.dmdev.premo.navigation.PmRouter

abstract class PresentationModel(
    private val args: Args
) {

    @Serializable
    abstract class Args : Saveable {
        @Transient var state: PmState? = null
        @Transient var tag: String? = null
        @Transient internal var parent: PresentationModel? = null
    }

    private val pmState: PmState? get() = args.state

    val pmScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var pmInForegroundScope: CoroutineScope? = null
        private set

    val tag: String = pmState?.tag ?: args.tag ?: randomUUID()

    val parentPm: PresentationModel? = args.parent

    private var routerOrNull: PmRouter? = null

    @Suppress("FunctionName")
    protected fun Router(initialPmArgs: Args, pmFactory: PmFactory): PmRouter {
        return routerOrNull ?: PmRouter(
            initialPmArgs = initialPmArgs,
            hostPm = this,
            pmFactory = pmFactory,
            backStackState = pmState?.routerState
        ).also { router ->
            routerOrNull = router
        }
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <PM : PresentationModel> saveableChild(
        args: Args,
        pmFactory: PmFactory,
        key: String
    ): PM {
        args.state = pmState?.childrenStates?.get(key)
        args.parent = this
        val pm: PM = pmFactory.createPm(args) as PM
        children[key] = pm
        pm.moveLifecycleTo(lifecycleState.value)
        return pm
    }

    @Suppress("UNCHECKED_CAST", "FunctionName")
    inline fun <reified T> SaveableState(
        initialValue: T,
        key: String
    ): State<T> {
        val saver = when {
            T::class == Boolean::class -> BooleanSaver
            T::class == Byte::class -> ByteSaver
            T::class == Short::class -> ShortSaver
            T::class == Int::class -> IntSaver
            T::class == Long::class -> LongSaver
            T::class == Float::class -> FloatSaver
            T::class == Double::class -> DoubleSaver
            T::class == Char::class -> CharSaver
            T::class == String::class -> StringSaver
            else -> SaveableSaver
        }

        return SaveableState(initialValue, key, saver as Saver<T, Saveable>)
    }

    @Suppress("UNCHECKED_CAST", "FunctionName")
    fun <T, S : Saveable> SaveableState(
        initialValue: T,
        key: String,
        saver: Saver<T, S>
    ): State<T> {
        val pmState = this.pmState
        val state: State<T> = if (pmState?.states?.containsKey(key) == true) {
            State(saver.restore(pmState.states[key] as S))
        } else {
            State(initialValue)
        }
        saveableStates[key] = SaveableState(state, saver)
        return state
    }

    private class SaveableState<T, S : Saveable>(
        val state: State<T>,
        val saver: Saver<T, S>
    ) {
        @Suppress("UNCHECKED_CAST")
        val saveableValue: Saveable
            get() = saver.save(state.value)
    }

    protected var <T> State<T>.value: T
        get() = mutableStateFlow.value
        set(value) {
            mutableStateFlow.value = value
        }

    private val saveableStates = mutableMapOf<String, SaveableState<*, *>>()
    private val children = mutableMapOf<String, PresentationModel>()

    internal val lifecycleState = MutableStateFlow(LifecycleState.INITIALIZED)
    private val lifecycleEvent = MutableSharedFlow<LifecycleEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @Suppress("MemberVisibilityCanBePrivate")
    protected open fun onCreate() {
    }

    @Suppress("MemberVisibilityCanBePrivate")
    protected open fun onForeground() {
    }

    @Suppress("MemberVisibilityCanBePrivate")
    protected open fun onBackground() {
    }

    @Suppress("MemberVisibilityCanBePrivate")
    protected open fun onDestroy() {
    }

    protected open fun handleNavigationMessage(message: NavigationMessage) {
        parentPm?.handleNavigationMessage(message)
    }

    open fun back() {
        val router = routerOrNull
        if (router != null && router.pmStack.value.size > 1) {
            router.pop()
        } else {
            parentPm?.back()
        }
    }

    open fun handleBack(): Boolean {
        val router = routerOrNull
        if (router != null) {
            val handledByNestedPm =
                router.pmStack.value.lastOrNull()?.pm?.handleBack() ?: false
            if (handledByNestedPm.not()) {
                if (router.pmStack.value.size > 1) {
                    router.pop()
                    return true
                }
            } else {
                return true
            }
            return false
        } else {
            return false
        }
    }

    internal fun saveState(): PmState {

        val router = routerOrNull
        val routerState = router?.pmStack?.value?.map { entry ->
            BackStackEntryState(
                args = entry.args,
                pmState = entry.pm.saveState()
            )
        } ?: listOf()

        return PmState(
            tag = tag,
            routerState = routerState,
            childrenStates = children.mapValues { entry -> entry.value.saveState() },
            states = saveableStates.mapValues { entry -> entry.value.saveableValue }
        )
    }

    internal fun moveLifecycleTo(targetLifecycle: LifecycleState) {

        fun moveChildren(targetLifecycle: LifecycleState) {
            children.forEach { entry ->
                entry.value.moveLifecycleTo(targetLifecycle)
            }
            routerOrNull?.pmStack?.value?.lastOrNull()?.pm?.moveLifecycleTo(targetLifecycle)
        }

        fun doOnCreate() {
            lifecycleState.value = LifecycleState.CREATED
            lifecycleEvent.tryEmit(LifecycleEvent.ON_CREATE)
            onCreate()
            moveChildren(LifecycleState.CREATED)
        }

        fun doOnForeground() {
            pmInForegroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            lifecycleState.value = LifecycleState.IN_FOREGROUND
            lifecycleEvent.tryEmit(LifecycleEvent.ON_FOREGROUND)
            onForeground()
            moveChildren(LifecycleState.IN_FOREGROUND)
        }

        fun doOnBackground() {
            moveChildren(LifecycleState.CREATED)
            lifecycleState.value = LifecycleState.CREATED
            lifecycleEvent.tryEmit(LifecycleEvent.ON_BACKGROUND)
            onBackground()
            pmInForegroundScope?.cancel()
        }

        fun doOnDestroy() {
            moveChildren(LifecycleState.DESTROYED)
            lifecycleState.value = LifecycleState.DESTROYED
            lifecycleEvent.tryEmit(LifecycleEvent.ON_DESTROY)
            onDestroy()
            pmScope.cancel()
        }

        when (targetLifecycle) {
            LifecycleState.INITIALIZED -> {
                // do nothing, initial lifecycle state is INITIALIZED
            }
            LifecycleState.CREATED -> {
                when (lifecycleState.value) {
                    LifecycleState.INITIALIZED -> {
                        doOnCreate()
                    }
                    LifecycleState.IN_FOREGROUND -> {
                        doOnBackground()
                    }
                    else -> { /*do nothing */
                    }
                }
            }
            LifecycleState.IN_FOREGROUND -> {
                when (lifecycleState.value) {
                    LifecycleState.INITIALIZED -> {
                        doOnCreate()
                        doOnForeground()
                    }
                    LifecycleState.CREATED -> {
                        doOnForeground()
                    }
                    else -> { /*do nothing */
                    }
                }
            }
            LifecycleState.DESTROYED -> {
                when (lifecycleState.value) {
                    LifecycleState.INITIALIZED -> {
                        doOnDestroy()
                    }
                    LifecycleState.CREATED -> {
                        doOnDestroy()
                    }
                    LifecycleState.IN_FOREGROUND -> {
                        doOnBackground()
                        doOnDestroy()
                    }
                    LifecycleState.DESTROYED -> { /*do nothing */
                    }
                }
            }
        }
    }
}