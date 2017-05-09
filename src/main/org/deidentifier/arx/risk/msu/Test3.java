/*
 * ARX: Powerful Data Anonymization
 * Copyright 2012 - 2016 Fabian Prasser, Florian Kohlmayer and contributors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.deidentifier.arx.risk.msu;

import org.apache.mahout.math.Arrays;

public class Test3 {

    public static void main(String[] args) {
        
        // From sdcMicro handbook
        int[][] data = new int[][] {
                new int[] {1, 2, 2, 1},
                new int[] {1, 2, 1, 1},
                new int[] {1, 2, 1, 1},
                new int[] {3, 3, 1, 5},
                new int[] {4, 3, 1, 4},
                new int[] {4, 3, 1, 1},
                new int[] {6, 2, 1, 5},
                new int[] {1, 2, 2, 1},
        };
        print(data);
        System.out.println("\n-----\n"); 
        SUDA2Result result1 = new SUDA2(data).suda2(5);
        System.out.println(result1.toString());
        System.out.println("\n-----\n");
        SUDA2Result result2 = new ExhaustiveSearch(data).exhaustive();
        System.out.println(result2.toString());
    }

    /**
     * Prints the data
     * @param data
     */
    private static void print(int[][] data) {
        for (int[] row : data) {
            System.out.println(" - " + Arrays.toString(row));
        }
    }
}
