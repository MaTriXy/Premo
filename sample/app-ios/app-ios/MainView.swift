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

import SwiftUI
import Common

struct MainView: View {
    
    private let delegate: PmDelegate<MainPm>
    private let pm: MainPm
    
    @ObservedObject
    private var currentPm: ObservableState<PresentationModel>
    
    init(delegate: PmDelegate<MainPm>) {
        self.delegate = delegate
        self.pm = delegate.presentationModel
        currentPm = ObservableState(pm.navigation.currentTopState)
    }
    
    var body: some View {
        VStack {
            
            HStack {
                Button(action: {
                    delegate.presentationModel.messageHandler.handle(message: SystemBackMessage())
                }) { Text("Back") }
                .padding()
                Spacer()
            }
            
            Spacer()
            
            switch currentPm.value {
            case let pm as SamplesPm: SamplesView(pm: pm)
            case let pm as CounterPm: CounterView(pm: pm)
            case let pm as StackNavigationPm: StackNavigationView(pm: pm)
            case let pm as BottomNavigationPm: BottomNavigationView(pm: pm)
            default: EmptyView()
            }
            
            Spacer()
        }
    }
}
