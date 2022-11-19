/*
 * ARX Data Anonymization Tool
 * Copyright 2012 - 2022 Fabian Prasser and contributors
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

package org.deidentifier.arx.distributed;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.deidentifier.arx.ARXConfiguration;
import org.deidentifier.arx.AttributeType;
import org.deidentifier.arx.AttributeType.Hierarchy;
import org.deidentifier.arx.Data;
import org.deidentifier.arx.criteria.DistinctLDiversity;
import org.deidentifier.arx.criteria.KAnonymity;
import org.deidentifier.arx.distributed.ARXDistributedAnonymizer.DistributionStrategy;
import org.deidentifier.arx.distributed.ARXDistributedAnonymizer.PartitioningStrategy;
import org.deidentifier.arx.distributed.ARXDistributedAnonymizer.TransformationStrategy;
import org.deidentifier.arx.exceptions.RollbackRequiredException;
import org.deidentifier.arx.io.CSVHierarchyInput;
import org.deidentifier.arx.metric.Metric;

/**
 * Example
 *
 * @author Fabian Prasser
 */
public class Main {

    /**
     * Loads a dataset from disk
     * @param dataset
     * @return
     * @throws IOException
     */
    public static Data createData(final String dataset) throws IOException {

        Data data = Data.create("data/" + dataset + ".csv", StandardCharsets.UTF_8, ';');

        // Read generalization hierarchies
        FilenameFilter hierarchyFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if (name.matches(dataset + "_hierarchy_(.)+.csv")) {
                    return true;
                } else {
                    return false;
                }
            }
        };

        // Create definition
        File testDir = new File("data/");
        File[] genHierFiles = testDir.listFiles(hierarchyFilter);
        Pattern pattern = Pattern.compile("_hierarchy_(.*?).csv");
        for (File file : genHierFiles) {
            Matcher matcher = pattern.matcher(file.getName());
            if (matcher.find()) {
                CSVHierarchyInput hier = new CSVHierarchyInput(file, StandardCharsets.UTF_8, ';');
                String attributeName = matcher.group(1);
                data.getDefinition().setAttributeType(attributeName, Hierarchy.create(hier.getHierarchy()));
            }
        }

        return data;
    }

    /**
     * Entry point.
     *
     * @param args the arguments
     * @throws IOException
     * @throws RollbackRequiredException 
     * @throws ExecutionException 
     * @throws InterruptedException 
     */
    public static void main(String[] args) throws IOException, RollbackRequiredException, InterruptedException, ExecutionException {
        playground();
    }
    
    private static void playground() throws IOException, RollbackRequiredException, InterruptedException, ExecutionException {

        Data data = createData("adult");
        
        // K-Anonymity
        for (int threads = 1; threads < 5; threads ++) {
            for (int k : new int[] { 5 }) {
                ARXConfiguration config = ARXConfiguration.create();
                config.addPrivacyModel(new KAnonymity(k));
                config.setQualityModel(Metric.createLossMetric(0.5d));
                config.setSuppressionLimit(1d);
    
                // Anonymize
                ARXDistributedAnonymizer anonymizer = new ARXDistributedAnonymizer(threads,
                                                                                   PartitioningStrategy.SORTED,
                                                                                   DistributionStrategy.LOCAL,
                                                                                   TransformationStrategy.GLOBAL_MINIMUM);
                ARXDistributedResult result = anonymizer.anonymize(data, config);
    
                System.out.println("--------------------------");
                System.out.println("Records: " + result.getOutput().getNumRows());
                System.out.println("Number of threads: " + threads);
                for (Entry<String, List<Double>> entry : result.getQuality().entrySet()) {
                    System.out.println(entry.getKey() + ": " + getAverage(entry.getValue()));
                }
    
                // Timing
                System.out.println("Preparation time: " + result.getTimePrepare());
                System.out.println("Anonymization time: " + result.getTimeAnonymize());
                System.out.println("Postprocessing time: " + result.getTimePostprocess());
            }
        }
    }
    
    /**
     * Benchmarking
     * @throws IOException
     * @throws RollbackRequiredException
     * @throws InterruptedException
     * @throws ExecutionException
     */
    private static void benchmark() throws IOException, RollbackRequiredException, InterruptedException, ExecutionException {

        Data data = createData("ihis");
        
        BufferedWriter out = new BufferedWriter(new FileWriter(new File("result.csv")));
        
        out.write("Dataset;k;l;Threads;Granularity;Time\n");
        out.flush();
        
        int run = 1;
        
        // K-Anonymity
        for (int k : new int[] {5, 11}) {
            for (int threads = 1; threads <= 64; threads++) {
                for (int i = 0; i < 3; i++) {
                    
                    System.out.println("Run " + run + " of " + (2 * 64 * 3) * 2);
                    
                    ARXConfiguration config = ARXConfiguration.create();
                    config.addPrivacyModel(new KAnonymity(k));
                    config.setQualityModel(Metric.createLossMetric(0d));
                    config.setSuppressionLimit(1d);
                    
                    // Anonymize
                    ARXDistributedAnonymizer anonymizer = new ARXDistributedAnonymizer(threads, 
                                                                                       PartitioningStrategy.SORTED, 
                                                                                       DistributionStrategy.LOCAL,
                                                                                       TransformationStrategy.LOCAL);
                    ARXDistributedResult result = anonymizer.anonymize(data, config);
                    
                    // Print
                    if (i==2) {
                        out.write("Ihis;");
                        out.write(k+";");
                        out.write(";");
                        out.write(threads+";");
                        out.write(getAverage(result.getQuality().get("Granularity"))+";");
                        out.write(result.getTimeAnonymize()+"\n");
                        out.flush();
                    }
                }
            }
        }
        
        // L-Diversity
        data.getDefinition().setAttributeType("EDUC", AttributeType.SENSITIVE_ATTRIBUTE);
        for (int k : new int[] {3, 5}) {
            for (int threads = 1; threads <= 64; threads++) {
                for (int i = 0; i < 3; i++) {
                    
                    System.out.println("Run " + run + " of " + (2 * 64 * 3) * 2);
                    
                    ARXConfiguration config = ARXConfiguration.create();
                    config.addPrivacyModel(new DistinctLDiversity("EDUC", k));
                    config.setQualityModel(Metric.createLossMetric(0d));
                    config.setSuppressionLimit(1d);
                    
                    // Anonymize
                    ARXDistributedAnonymizer anonymizer = new ARXDistributedAnonymizer(threads, 
                                                                                       PartitioningStrategy.SORTED, 
                                                                                       DistributionStrategy.LOCAL, 
                                                                                       TransformationStrategy.LOCAL);
                    ARXDistributedResult result = anonymizer.anonymize(data, config);
                    
                    // Print
                    if (i==2) {
                        out.write("Ihis;");
                        out.write(";");
                        out.write(k+";");
                        out.write(threads+";");
                        out.write(getAverage(result.getQuality().get("Granularity"))+";");
                        out.write(result.getTimeAnonymize()+"\n");
                        out.flush();
                    }
//                    System.out.println("--------------------------");
//                    System.out.println("Number of threads: " + threads);
//                    for (Entry<String, List<Double>> entry : result.getQuality().entrySet()) {
//                        System.out.println(entry.getKey()+": " + getAverage(entry.getValue()));
//                    }
//                    
//                    // Timing
//                    System.out.println("Preparation time: " + result.getTimePrepare());
//                    System.out.println("Anonymization time: " + result.getTimeAnonymize());
//                    System.out.println("Postprocessing time: " + result.getTimePostprocess());
                }
            }
        }
        
        out.close();
    }
    
    /**
     * Returns the average of the given values
     * @param values
     * @return
     */
    private static double getAverage(List<Double> values) {
        double result = 0d;
        for (Double value : values) {
            result += value;
        }
        return result / (double)values.size();
    }
}
