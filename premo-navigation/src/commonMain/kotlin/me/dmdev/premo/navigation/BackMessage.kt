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

import me.dmdev.premo.PmMessage
import me.dmdev.premo.PresentationModel

object BackMessage : PmMessage

fun PresentationModel.handleBack(): Boolean {
    return messageHandler.handle(BackMessage)
}

fun PresentationModel.back() {
    messageHandler.send(BackMessage)
}

fun StackNavigator.handleBack(): Boolean {
    var handled = currentTop?.handleBack() ?: false
    if (!handled && backStack.size > 1) {
        pop()
        handled = true
    }
    return handled
}

fun SetNavigator.handleBack(): Boolean {
    return if (current.handleBack()) {
        true
    } else if (values.indexOf(current) > 0) {
        changeCurrent(0)
        true
    } else {
        false
    }
}

fun MasterDetailNavigator<*, *>.handleBack(): Boolean {
    var handled = detail?.handleBack() ?: false
    if (!handled && detail != null) {
        changeDetail(null)
        handled = true
    }
    if (!handled) {
        handled = master.handleBack()
    }
    return handled
}

fun DialogNavigator<*, *>.handleBack(): Boolean {
    return if (isShowing) {
        dismiss()
        true
    } else {
        false
    }
}
