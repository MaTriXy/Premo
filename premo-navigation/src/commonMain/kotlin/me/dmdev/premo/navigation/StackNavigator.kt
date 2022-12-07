/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2022 Dmitriy Gorbunov (dmitriy.goto@gmail.com)
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

package me.dmdev.premo.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import me.dmdev.premo.*
import me.dmdev.premo.PmLifecycle.State.*

interface StackNavigator : StackNavigation {
    fun setBackStack(pmList: List<PresentationModel>)
}

fun StackNavigator.push(pm: PresentationModel) {
    setBackStack(backstack.plus(pm))
}

fun StackNavigator.pop(): Boolean {
    if (backstack.isEmpty()) return false
    setBackStack(backstack.dropLast(1))
    return true
}

fun StackNavigator.popToRoot(): Boolean {
    if (backstack.isEmpty()) return false
    setBackStack(backstack.subList(0, 1))
    return true
}

fun StackNavigator.popUntil(predicate: (PresentationModel) -> Boolean) {
    setBackStack(backstack.takeWhile(predicate))
}

fun StackNavigator.replaceTop(pm: PresentationModel) {
    setBackStack(backstack.dropLast(1).plus(pm))
}

fun StackNavigator.replaceAll(pm: PresentationModel) {
    setBackStack(listOf(pm))
}

fun PresentationModel.StackNavigator(
    initialDescription: PmDescription? = null,
    key: String = "stack_navigator"
): StackNavigator {
    val navigator = StackNavigatorImpl(lifecycle, scope)
    stateHandler.setSaver(key) {
        navigator.backstack.map { pm -> Pair(pm.description, pm.tag) }
    }
    val savedBackStack: List<PresentationModel> =
        stateHandler.getSaved<List<Pair<PmDescription, String>>>(key)
            ?.map { (description, tag) -> Child(description, tag) }
            ?: listOf()
    if (savedBackStack.isNotEmpty()) {
        navigator.setBackStack(savedBackStack)
    } else if (initialDescription != null) {
        navigator.push(Child(initialDescription))
    }
    return navigator
}

internal class StackNavigatorImpl(
    private val lifecycle: PmLifecycle,
    private val scope: CoroutineScope
) : StackNavigator {

    private val _backstackState = MutableStateFlow<List<PresentationModel>>(listOf())
    override val backstackState: StateFlow<List<PresentationModel>> = _backstackState

    private var backstack: List<PresentationModel>
        get() = backstackState.value
        private set(value) {
            _backstackState.value = value
        }

    override val currentTopState: StateFlow<PresentationModel?> =
        backstackState.map { it.lastOrNull() }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )

    init {
        subscribeToLifecycle()
    }

    @ExperimentalPremoApi
    override val backstackChanges: Flow<BackstackChange> = flow {
        var oldPmStack: List<PresentationModel> = backstackState.value
        _backstackState.collect { newPmStack ->

            val oldTopPm = oldPmStack.lastOrNull()
            val newTopPm = newPmStack.lastOrNull()

            val pmStackChange = if (newTopPm != null && oldTopPm != null) {
                when {
                    oldTopPm === newTopPm -> {
                        BackstackChange.Set(newTopPm)
                    }
                    oldPmStack.any { it === newTopPm } -> {
                        BackstackChange.Pop(newTopPm, oldTopPm)
                    }
                    else -> {
                        BackstackChange.Push(newTopPm, oldTopPm)
                    }
                }
            } else if (newTopPm != null) {
                BackstackChange.Set(newTopPm)
            } else {
                BackstackChange.Empty
            }

            emit(pmStackChange)
            oldPmStack = newPmStack
        }
    }

    override fun setBackStack(pmList: List<PresentationModel>) {
        val iterator = pmList.listIterator()
        while (iterator.hasNext()) {
            val pm = iterator.next()
            if (iterator.hasNext()) {
                pm.lifecycle.moveTo(CREATED)
            } else {
                pm.lifecycle.moveTo(lifecycle.state)
            }
        }
        backstack.forEach { pm ->
            if (pmList.contains(pm).not()) {
                pm.lifecycle.moveTo(DESTROYED)
            }
        }
        backstack = pmList
    }

    private fun subscribeToLifecycle() {
        lifecycle.addObserver { lifecycle, event ->
            when (lifecycle.state) {
                CREATED,
                DESTROYED -> {
                    backstack.forEach { pm ->
                        pm.lifecycle.moveTo(lifecycle.state)
                    }
                }

                IN_FOREGROUND -> {
                    currentTop?.lifecycle?.moveTo(lifecycle.state)
                }
            }
        }
    }
}