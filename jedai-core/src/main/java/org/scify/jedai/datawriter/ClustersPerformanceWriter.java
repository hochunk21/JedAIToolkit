/*
* Copyright [2016-2018] [George Papadakis (gpapadis@yahoo.gr)]
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
 */
package org.scify.jedai.datawriter;

import org.scify.jedai.utilities.datastructures.BilateralDuplicatePropagation;
import org.scify.jedai.utilities.datastructures.AbstractDuplicatePropagation;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateProcessor;
import org.apache.jena.update.UpdateRequest;
import org.scify.jedai.datamodel.Comparison;
import org.scify.jedai.datamodel.EquivalenceCluster;

import com.esotericsoftware.minlog.Log;
import gnu.trove.iterator.TIntIterator;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

import org.scify.jedai.datamodel.EntityProfile;
import org.scify.jedai.datamodel.IdDuplicates;

/**
 *
 * @author gap2
 */
public class ClustersPerformanceWriter {

    private double fMeasure;
    private double precision;
    private double recall;
    private double totalMatches;

    private final AbstractDuplicatePropagation abstractDP;
    private final EquivalenceCluster[] entityClusters;
    
    private String dbpassword;
	private String dbtable;
	private String dbuser;
	private boolean ssl;
	private String endpointURL;
	private String endpointGraph;

    public ClustersPerformanceWriter(EquivalenceCluster[] clusters, AbstractDuplicatePropagation adp) {
        abstractDP = adp;
        abstractDP.resetDuplicates();
        entityClusters = clusters;
    }
    
    public void setPassword(String password) {
        this.dbpassword = password;
    }

	public void setTable(String table) {
        this.dbtable = table;
    }

    public void setUser(String user) {
        this.dbuser = user;
    }
    
    public void setSSL(boolean ssl) {
        this.ssl = ssl;
    }
    
    public void setEndpointURL(String endpointURL) {
        this.endpointURL = endpointURL;
    }

    public void setEndpointGraph(String endpointGraph) {
        this.endpointGraph = endpointGraph;
    }
    
    private Connection getMySQLconnection(String dbURL) throws IOException {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            return DriverManager.getConnection("jdbc:" + dbURL + "?user=" + dbuser + "&password=" + dbpassword);
        } catch (Exception ex) {
            Log.error("Error with database connection!", ex);
            return null;
        }
    }
    
    private Connection getPostgreSQLconnection(String dbURL) throws IOException {
        try {
            final Properties props = new Properties();
            if (!(dbuser == null)) {
                props.setProperty("user", dbuser);
            }
            if (!(dbpassword == null)) {
                props.setProperty("password", dbpassword);
            }
            if (ssl) {
                props.setProperty("ssl", "true");
            }
            return DriverManager.getConnection("jdbc:" + dbURL, props);
        } catch (Exception ex) {
            Log.error("Error with database connection!", ex);
            return null;
        }
    }

    public int getDetectedDuplicates() {
        return abstractDP.getNoOfDuplicates();
    }

    public int getEntityClusters() {
        return entityClusters.length;
    }

    public int getExistingDuplicates() {
        return abstractDP.getExistingDuplicates();
    }

    public double getFMeasure() {
        return fMeasure;
    }

    public double getPrecision() {
        return precision;
    }

    public double getRecall() {
        return recall;
    }

    public double getTotalMatches() {
        return totalMatches;
    }

    public void printStatistics(double overheadTime, String methodName, String methodConfiguration) {
        System.out.println("\n\n\n**************************************************");
        System.out.println("Performance of : " + methodName);
        System.out.println("Configuration : " + methodConfiguration);
        System.out.println("**************************************************");
        System.out.println("No of clusters\t:\t" + entityClusters.length);
        System.out.println("Detected duplicates\t:\t" + abstractDP.getNoOfDuplicates());
        System.out.println("Existing duplicates\t:\t" + abstractDP.getExistingDuplicates());
        System.out.println("Total matches\t:\t" + totalMatches);
        System.out.println("Precision\t:\t" + precision);
        System.out.println("Recall\t:\t" + recall);
        System.out.println("F-Measure\t:\t" + fMeasure);
        System.out.println("Overhead time\t:\t" + overheadTime);
    }

    public void printDetailedResultsToCSV(List<EntityProfile> profilesD1, List<EntityProfile> profilesD2, String outputFile) throws FileNotFoundException {
        if (entityClusters.length == 0) {
            Log.warn("Empty set of equivalence clusters given as input!");
            return;
        }

        totalMatches = 0;
        final PrintWriter pw = new PrintWriter(new File(outputFile));
        StringBuilder sb = new StringBuilder();

        abstractDP.resetDuplicates();
        if (abstractDP instanceof BilateralDuplicatePropagation) { // Clean-Clean ER
            for (EquivalenceCluster cluster : entityClusters) {                
                if (cluster.getEntityIdsD1().size() != 1
                        || cluster.getEntityIdsD2().size() != 1) {
                    continue;
                }

                totalMatches++;

                final int entityId1 = cluster.getEntityIdsD1().get(0);
                final EntityProfile profile1 = profilesD1.get(entityId1);

                final int entityId2 = cluster.getEntityIdsD2().get(0);
                final EntityProfile profile2 = profilesD2.get(entityId2);

                final int originalDuplicates = abstractDP.getNoOfDuplicates();
                abstractDP.isSuperfluous(entityId1, entityId2);
                final int newDuplicates = abstractDP.getNoOfDuplicates();
                
                sb.append(profile1.getEntityUrl()).append(",");
                sb.append(profile2.getEntityUrl()).append(",");
                if (originalDuplicates == newDuplicates) {
                    sb.append("FP,"); //false positive
                } else { // originalDuplicates < newDuplicates
                    sb.append("TP,"); // true positive
                }
                sb.append("Profile 1:[").append(profile1).append("]");
                sb.append("Profile 2:[").append(profile2).append("]").append("\n");
            }

            for (IdDuplicates duplicatesPair : abstractDP.getFalseNegatives()) {
                final EntityProfile profile1 = profilesD1.get(duplicatesPair.getEntityId1());
                final EntityProfile profile2 = profilesD2.get(duplicatesPair.getEntityId2());

                sb.append(profile1.getEntityUrl()).append(",");
                sb.append(profile2.getEntityUrl()).append(",");
                sb.append("FN,"); // false negative
                sb.append("Profile 1:[").append(profile1).append("]");
                sb.append("Profile 2:[").append(profile2).append("]").append("\n");
            }
        } else { // Dirty ER
            for (EquivalenceCluster cluster : entityClusters) {
                final int[] duplicatesArray = cluster.getEntityIdsD1().toArray();

                for (int i = 0; i < duplicatesArray.length; i++) {
                    for (int j = i + 1; j < duplicatesArray.length; j++) {
                        totalMatches++;

                        final EntityProfile profile1 = profilesD1.get(duplicatesArray[i]);
                        final EntityProfile profile2 = profilesD1.get(duplicatesArray[j]);

                        final int originalDuplicates = abstractDP.getNoOfDuplicates();
                        abstractDP.isSuperfluous(duplicatesArray[i], duplicatesArray[j]);
                        final int newDuplicates = abstractDP.getNoOfDuplicates();

                        sb.append(profile1.getEntityUrl()).append(",");
                        sb.append(profile2.getEntityUrl()).append(",");
                        if (originalDuplicates == newDuplicates) {
                            sb.append("FP,"); //false positive
                        } else { // originalDuplicates < newDuplicates
                            sb.append("TP,"); // true positive
                        }
                        sb.append("Profile 1:[").append(profile1).append("]");
                        sb.append("Profile 2:[").append(profile2).append("]").append("\n");
                    }
                }
            }

            for (IdDuplicates duplicatesPair : abstractDP.getFalseNegatives()) {
                final EntityProfile profile1 = profilesD1.get(duplicatesPair.getEntityId1());
                final EntityProfile profile2 = profilesD1.get(duplicatesPair.getEntityId2());

                sb.append(profile1.getEntityUrl()).append(",");
                sb.append(profile2.getEntityUrl()).append(",");
                sb.append("FN,"); // false negative
                sb.append("Profile 1:[").append(profile1).append("]");
                sb.append("Profile 2:[").append(profile2).append("]").append("\n");
            }
        }

        if (0 < totalMatches) {
            precision = abstractDP.getNoOfDuplicates() / totalMatches;
        } else {
            precision = 0;
        }
        recall = ((double) abstractDP.getNoOfDuplicates()) / abstractDP.getExistingDuplicates();
        if (0 < precision && 0 < recall) {
            fMeasure = 2 * precision * recall / (precision + recall);
        } else {
            fMeasure = 0;
        }

        pw.write("Precision\t:\t" + precision + "\n");
        pw.write("Recall\t:\t" + recall + "\n");
        pw.write("F-Measure\t:\t" + fMeasure + "\n");
        pw.write(sb.toString());
        pw.close();
    }

    public void printDetailedResultsToRDF(List<EntityProfile> profilesD1, List<EntityProfile> profilesD2, String outputFile) throws FileNotFoundException {
        if (entityClusters.length == 0) {
            Log.warn("Empty set of equivalence clusters given as input!");
            return;
        }

        totalMatches = 0;
        final PrintWriter printWriter = new PrintWriter(new File(outputFile));

        printWriter.println("<?xml version=\"1.0\"?>");
	    printWriter.println();
	    printWriter.println("<rdf:RDF");
	    printWriter.println("xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"");
	    printWriter.println("xmlns:obj=\"https://www.w3schools.com/rdf/\">");
	    
        abstractDP.resetDuplicates();
        if (abstractDP instanceof BilateralDuplicatePropagation) { // Clean-Clean ER
            for (EquivalenceCluster cluster : entityClusters) {                
                if (cluster.getEntityIdsD1().size() != 1
                        || cluster.getEntityIdsD2().size() != 1) {
                    continue;
                }

                totalMatches++;

                final int entityId1 = cluster.getEntityIdsD1().get(0);
                final EntityProfile profile1 = profilesD1.get(entityId1);

                final int entityId2 = cluster.getEntityIdsD2().get(0);
                final EntityProfile profile2 = profilesD2.get(entityId2);

                final int originalDuplicates = abstractDP.getNoOfDuplicates();
                abstractDP.isSuperfluous(entityId1, entityId2);
                final int newDuplicates = abstractDP.getNoOfDuplicates();
                
            	printWriter.println();

            	printWriter.println("<rdf:Description rdf:about=\""+cluster.toString()+"\">");

            	printWriter.print("<obj:"+"url1"+">");
            	printWriter.print(profile1.getEntityUrl().replace("&", "")+"");
            	printWriter.println("</obj:"+"url1>");
            	
            	printWriter.print("<obj:"+"url2"+">");
            	printWriter.print(profile2.getEntityUrl().replace("&", "")+"");
            	printWriter.println("</obj:"+"url2>");
            	
                
                printWriter.print("<obj:"+"pairType"+">");
                if (originalDuplicates == newDuplicates) {
                	printWriter.print("FP"); //false positive
                } else { // originalDuplicates < newDuplicates
                	printWriter.print("TP"); // true positive
                }               
            	printWriter.println("</obj:"+"pairType>");
            	
            	printWriter.print("<obj:"+"Profile1"+">");
            	printWriter.print((profile1+"").replace("&", ""));
            	printWriter.println("</obj:"+"Profile1>");
            	
            	printWriter.print("<obj:"+"Profile2"+">");
            	printWriter.print((profile2+"").replace("&", ""));
            	printWriter.println("</obj:"+"Profile2>");
                
            	printWriter.println("</rdf:Description>");
            }

            for (IdDuplicates duplicatesPair : abstractDP.getFalseNegatives()) {
                final EntityProfile profile1 = profilesD1.get(duplicatesPair.getEntityId1());
                final EntityProfile profile2 = profilesD2.get(duplicatesPair.getEntityId2());

            	printWriter.println();

                printWriter.println("<rdf:Description rdf:about=\""+duplicatesPair.toString()+"\">");

            	printWriter.print("<obj:"+"url1"+">");
            	printWriter.print(profile1.getEntityUrl().replace("&", "")+"");
            	printWriter.println("</obj:"+"url1>");
            	
            	printWriter.print("<obj:"+"url2"+">");
            	printWriter.print(profile2.getEntityUrl().replace("&", "")+"");
            	printWriter.println("</obj:"+"url2>");
            	
                
                printWriter.print("<obj:"+"pairType"+">");
            	printWriter.print("FN"); // false negative
            	printWriter.println("</obj:"+"pairType>");
            	
            	printWriter.print("<obj:"+"Profile1"+">");
            	printWriter.print((profile1+"").replace("&", ""));
            	printWriter.println("</obj:"+"Profile1>");
            	
            	printWriter.print("<obj:"+"Profile2"+">");
            	printWriter.print((profile2+"").replace("&", ""));
            	printWriter.println("</obj:"+"Profile2>");
                
            	printWriter.println("</rdf:Description>");
            }
        } else { // Dirty ER
            for (EquivalenceCluster cluster : entityClusters) {
                final int[] duplicatesArray = cluster.getEntityIdsD1().toArray();

                for (int i = 0; i < duplicatesArray.length; i++) {
                    for (int j = i + 1; j < duplicatesArray.length; j++) {
                        totalMatches++;

                        final EntityProfile profile1 = profilesD1.get(duplicatesArray[i]);
                        final EntityProfile profile2 = profilesD1.get(duplicatesArray[j]);

                        final int originalDuplicates = abstractDP.getNoOfDuplicates();
                        abstractDP.isSuperfluous(duplicatesArray[i], duplicatesArray[j]);
                        final int newDuplicates = abstractDP.getNoOfDuplicates();
                        
                    	printWriter.println();

                        printWriter.println("<rdf:Description rdf:about=\""+cluster.toString()+"\">");

                    	printWriter.print("<obj:"+"url1"+">");
                    	printWriter.print(profile1.getEntityUrl().replace("&", "")+"");
                    	printWriter.println("</obj:"+"url1>");
                    	
                    	printWriter.print("<obj:"+"url2"+">");
                    	printWriter.print(profile2.getEntityUrl().replace("&", "")+"");
                    	printWriter.println("</obj:"+"url2>");
                    	
                        
                        printWriter.print("<obj:"+"pairType"+">");
                        if (originalDuplicates == newDuplicates) {
                        	printWriter.print("FP"); //false positive
                        } else { // originalDuplicates < newDuplicates
                        	printWriter.print("TP"); // true positive
                        }               
                    	printWriter.println("</obj:"+"pairType>");
                    	
                    	printWriter.print("<obj:"+"Profile1"+">");
                    	printWriter.print((profile1+"").replace("&", ""));
                    	printWriter.println("</obj:"+"Profile1>");
                    	
                    	printWriter.print("<obj:"+"Profile2"+">");
                    	printWriter.print((profile2+"").replace("&", ""));
                    	printWriter.println("</obj:"+"Profile2>");
                        
                    	printWriter.println("</rdf:Description>");
                    }
                }
            }

            for (IdDuplicates duplicatesPair : abstractDP.getFalseNegatives()) {
                final EntityProfile profile1 = profilesD1.get(duplicatesPair.getEntityId1());
                final EntityProfile profile2 = profilesD1.get(duplicatesPair.getEntityId2());

            	printWriter.println();

                printWriter.println("<rdf:Description rdf:about=\""+duplicatesPair.toString()+"\">");

            	printWriter.print("<obj:"+"url1"+">");
            	printWriter.print(profile1.getEntityUrl().replace("&", "")+"");
            	printWriter.println("</obj:"+"url1>");
            	
            	printWriter.print("<obj:"+"url2"+">");
            	printWriter.print(profile2.getEntityUrl().replace("&", "")+"");
            	printWriter.println("</obj:"+"url2>");
            	
                
                printWriter.print("<obj:"+"pairType"+">");
            	printWriter.print("FN"); // false negative
            	printWriter.println("</obj:"+"pairType>");
            	
            	printWriter.print("<obj:"+"Profile1"+">");
            	printWriter.print((profile1+"").replace("&", ""));
            	printWriter.println("</obj:"+"Profile1>");
            	
            	printWriter.print("<obj:"+"Profile2"+">");
            	printWriter.print((profile2+"").replace("&", ""));
            	printWriter.println("</obj:"+"Profile2>");
                
            	printWriter.println("</rdf:Description>");
            }
            
        }

        if (0 < totalMatches) {
            precision = abstractDP.getNoOfDuplicates() / totalMatches;
        } else {
            precision = 0;
        }
        recall = ((double) abstractDP.getNoOfDuplicates()) / abstractDP.getExistingDuplicates();
        if (0 < precision && 0 < recall) {
            fMeasure = 2 * precision * recall / (precision + recall);
        } else {
            fMeasure = 0;
        }

    	printWriter.println();

        printWriter.println("<rdf:Description rdf:about=\""+"STATS"+"\">");

        printWriter.print("<obj:"+"Precision"+">");
    	printWriter.print(precision+"");
    	printWriter.println("</obj:"+"Precision>");
    	
        printWriter.print("<obj:"+"Recall"+">");
    	printWriter.print(recall+"");
    	printWriter.println("</obj:"+"Recall>");
    	
        printWriter.print("<obj:"+"F-Measure"+">");
    	printWriter.print(fMeasure+"");
    	printWriter.println("</obj:"+"F-Measure>");

    	printWriter.println("</rdf:Description>");

        printWriter.println("</rdf:RDF>");
        printWriter.close();
    }
    
    public void printDetailedResultsToSPARQL(List<EntityProfile> profilesD1, List<EntityProfile> profilesD2, String endpointURL, String GraphName) throws FileNotFoundException {
        if (entityClusters.length == 0) {
            Log.warn("Empty set of equivalence clusters given as input!");
            return;
        }

        totalMatches = 0;
        StringBuilder sb = new StringBuilder();

        String sparqlQueryString1 = "INSERT DATA { "
        		+ "GRAPH "+GraphName+" { ";
        sb.append(sparqlQueryString1);
	    
        abstractDP.resetDuplicates();
        if (abstractDP instanceof BilateralDuplicatePropagation) { // Clean-Clean ER
            for (EquivalenceCluster cluster : entityClusters) {                
                if (cluster.getEntityIdsD1().size() != 1
                        || cluster.getEntityIdsD2().size() != 1) {
                    continue;
                }

                totalMatches++;

                final int entityId1 = cluster.getEntityIdsD1().get(0);
                final EntityProfile profile1 = profilesD1.get(entityId1);

                final int entityId2 = cluster.getEntityIdsD2().get(0);
                final EntityProfile profile2 = profilesD2.get(entityId2);

                final int originalDuplicates = abstractDP.getNoOfDuplicates();
                abstractDP.isSuperfluous(entityId1, entityId2);
                final int newDuplicates = abstractDP.getNoOfDuplicates();
                
                sb.append("<obj/"+"record/"+cluster.toString()+"> ");
            	sb.append("<url1> ");
            	sb.append("\""+profile1.getEntityUrl().replace("&", "")+""+"\".\n");
            	
            	sb.append("<obj/"+"record/"+cluster.toString()+"> ");
            	sb.append("<url2> ");
            	sb.append("\""+profile2.getEntityUrl().replace("&", "")+""+"\".\n");
            	
            	sb.append("<obj/"+"record/"+cluster.toString()+"> ");
            	sb.append("<pairType> ");
                if (originalDuplicates == newDuplicates) {
                	sb.append("\""+"FP"+"\".\n");//false positive
                } else { // originalDuplicates < newDuplicates
                	sb.append("\""+"TP"+"\".\n"); // true positive
                }               
            	
                sb.append("<obj/"+"record/"+cluster.toString()+"> ");
            	sb.append("<Profile1> ");
            	sb.append("\""+(profile1+"").replace("&", "")+"\".\n");
            	
            	sb.append("<obj/"+"record/"+cluster.toString()+"> ");
            	sb.append("<Profile2> ");
            	sb.append("\""+(profile2+"").replace("&", "")+"\".\n");
            	
            	//execute query every 1000 steps
            	if (totalMatches % 1000 == 0)
                {
                    sb.append("}\n }");
                    String sparqlQueryString = sb.toString();

                    //System.out.println(sparqlQueryString);
                    UpdateRequest update  = UpdateFactory.create(sparqlQueryString);
                    UpdateProcessor qexec = UpdateExecutionFactory.createRemote(update, endpointURL);
                    qexec.execute();
                    sb.setLength(0);
                    sb.append(sparqlQueryString1);
                }
                
            }

            if (totalMatches % 1000 != 0)
            {
            	sb.append("}\n }");
                String sparqlQueryString = sb.toString();

                //System.out.println(sparqlQueryString);
                UpdateRequest update  = UpdateFactory.create(sparqlQueryString);
                UpdateProcessor qexec = UpdateExecutionFactory.createRemote(update, endpointURL);
                qexec.execute();
                sb.setLength(0);
                sb.append(sparqlQueryString1);
            }
            
            int counter = 0;
            for (IdDuplicates duplicatesPair : abstractDP.getFalseNegatives()) {
                final EntityProfile profile1 = profilesD1.get(duplicatesPair.getEntityId1());
                final EntityProfile profile2 = profilesD2.get(duplicatesPair.getEntityId2());

                counter++;
                
                sb.append("<obj/"+"record/"+duplicatesPair.toString()+"> ");
            	sb.append("<url1> ");
            	sb.append("\""+profile1.getEntityUrl().replace("&", "")+""+"\".\n");
            	
            	sb.append("<obj/"+"record/"+duplicatesPair.toString()+"> ");
            	sb.append("<url2> ");
            	sb.append("\""+profile2.getEntityUrl().replace("&", "")+""+"\".\n");
            	
            	sb.append("<obj/"+"record/"+duplicatesPair.toString()+"> ");
            	sb.append("<pairType> ");    
            	sb.append("\""+"FN"+"\".\n"); // false negative
            	
                sb.append("<obj/"+"record/"+duplicatesPair.toString()+"> ");
            	sb.append("<Profile1> ");
            	sb.append("\""+(profile1+"").replace("&", "")+"\".\n");
            	
            	sb.append("<obj/"+"record/"+duplicatesPair.toString()+"> ");
            	sb.append("<Profile2> ");
            	sb.append("\""+(profile2+"").replace("&", "")+"\".\n");
            	
            	//execute query every 1000 steps
            	if (counter % 1000 == 0)
                {
                    sb.append("}\n }");
                    String sparqlQueryString = sb.toString();

                    //System.out.println(sparqlQueryString);
                    UpdateRequest update  = UpdateFactory.create(sparqlQueryString);
                    UpdateProcessor qexec = UpdateExecutionFactory.createRemote(update, endpointURL);
                    qexec.execute();
                    sb.setLength(0);
                    sb.append(sparqlQueryString1);
                }
            }
            
            if (counter % 1000 != 0)
            {
            	sb.append("}\n }");
                String sparqlQueryString = sb.toString();

                //System.out.println(sparqlQueryString);
                UpdateRequest update  = UpdateFactory.create(sparqlQueryString);
                UpdateProcessor qexec = UpdateExecutionFactory.createRemote(update, endpointURL);
                qexec.execute();
                sb.setLength(0);
                sb.append(sparqlQueryString1);
            }
        } else { // Dirty ER
            for (EquivalenceCluster cluster : entityClusters) {
                final int[] duplicatesArray = cluster.getEntityIdsD1().toArray();

                for (int i = 0; i < duplicatesArray.length; i++) {
                    for (int j = i + 1; j < duplicatesArray.length; j++) {
                        totalMatches++;

                        final EntityProfile profile1 = profilesD1.get(duplicatesArray[i]);
                        final EntityProfile profile2 = profilesD1.get(duplicatesArray[j]);

                        final int originalDuplicates = abstractDP.getNoOfDuplicates();
                        abstractDP.isSuperfluous(duplicatesArray[i], duplicatesArray[j]);
                        final int newDuplicates = abstractDP.getNoOfDuplicates();
                        
                        sb.append("<obj/"+"record/"+cluster.toString()+"> ");
                    	sb.append("<url1> ");
                    	sb.append("\""+profile1.getEntityUrl().replace("&", "")+""+"\".\n");
                    	
                    	sb.append("<obj/"+"record/"+cluster.toString()+"> ");
                    	sb.append("<url2> ");
                    	sb.append("\""+profile2.getEntityUrl().replace("&", "")+""+"\".\n");
                    	
                    	sb.append("<obj/"+"record/"+cluster.toString()+"> ");
                    	sb.append("<pairType> ");
                        if (originalDuplicates == newDuplicates) {
                        	sb.append("\""+"FP"+"\".\n");//false positive
                        } else { // originalDuplicates < newDuplicates
                        	sb.append("\""+"TP"+"\".\n"); // true positive
                        }               
                    	
                        sb.append("<obj/"+"record/"+cluster.toString()+"> ");
                    	sb.append("<Profile1> ");
                    	sb.append("\""+(profile1+"").replace("&", "")+"\".\n");
                    	
                    	sb.append("<obj/"+"record/"+cluster.toString()+"> ");
                    	sb.append("<Profile2> ");
                    	sb.append("\""+(profile2+"").replace("&", "")+"\".\n");
                    	
                    	//execute query every 1000 steps
                    	if (totalMatches % 1000 == 0)
                        {
                            sb.append("}\n }");
                            String sparqlQueryString = sb.toString();

                            //System.out.println(sparqlQueryString);
                            UpdateRequest update  = UpdateFactory.create(sparqlQueryString);
                            UpdateProcessor qexec = UpdateExecutionFactory.createRemote(update, endpointURL);
                            qexec.execute();
                            sb.setLength(0);
                            sb.append(sparqlQueryString1);
                        }
                    }
                }
                
                if (totalMatches % 1000 != 0)
                {
                	sb.append("}\n }");
                    String sparqlQueryString = sb.toString();

                    //System.out.println(sparqlQueryString);
                    UpdateRequest update  = UpdateFactory.create(sparqlQueryString);
                    UpdateProcessor qexec = UpdateExecutionFactory.createRemote(update, endpointURL);
                    qexec.execute();
                    sb.setLength(0);
                    sb.append(sparqlQueryString1);
                }
            }

            int counter2 = 0;
           
            for (IdDuplicates duplicatesPair : abstractDP.getFalseNegatives()) {
                final EntityProfile profile1 = profilesD1.get(duplicatesPair.getEntityId1());
                final EntityProfile profile2 = profilesD1.get(duplicatesPair.getEntityId2());

                counter2++;
                sb.append("<obj/"+"record/"+duplicatesPair.toString()+"> ");
            	sb.append("<url1> ");
            	sb.append("\""+profile1.getEntityUrl().replace("&", "")+""+"\".\n");
            	
            	sb.append("<obj/"+"record/"+duplicatesPair.toString()+"> ");
            	sb.append("<url2> ");
            	sb.append("\""+profile2.getEntityUrl().replace("&", "")+""+"\".\n");
            	
            	sb.append("<obj/"+"record/"+duplicatesPair.toString()+"> ");
            	sb.append("<pairType> ");    
            	sb.append("\""+"FN"+"\".\n"); // false negative
            	
                sb.append("<obj/"+"record/"+duplicatesPair.toString()+"> ");
            	sb.append("<Profile1> ");
            	sb.append("\""+(profile1+"").replace("&", "")+"\".\n");
            	
            	sb.append("<obj/"+"record/"+duplicatesPair.toString()+"> ");
            	sb.append("<Profile2> ");
            	sb.append("\""+(profile2+"").replace("&", "")+"\".\n");
            	
            	//execute query every 1000 steps
            	if (counter2 % 1000 == 0)
                {
                    sb.append("}\n }");
                    String sparqlQueryString = sb.toString();

                    //System.out.println(sparqlQueryString);
                    UpdateRequest update  = UpdateFactory.create(sparqlQueryString);
                    UpdateProcessor qexec = UpdateExecutionFactory.createRemote(update, endpointURL);
                    qexec.execute();
                    sb.setLength(0);
                    sb.append(sparqlQueryString1);
                }
            }
            
            if (counter2 % 1000 != 0)
            {
            	sb.append("}\n }");
                String sparqlQueryString = sb.toString();

                //System.out.println(sparqlQueryString);
                UpdateRequest update  = UpdateFactory.create(sparqlQueryString);
                UpdateProcessor qexec = UpdateExecutionFactory.createRemote(update, endpointURL);
                qexec.execute();
                sb.setLength(0);
                sb.append(sparqlQueryString1);
            }
        }

        if (0 < totalMatches) {
            precision = abstractDP.getNoOfDuplicates() / totalMatches;
        } else {
            precision = 0;
        }
        recall = ((double) abstractDP.getNoOfDuplicates()) / abstractDP.getExistingDuplicates();
        if (0 < precision && 0 < recall) {
            fMeasure = 2 * precision * recall / (precision + recall);
        } else {
            fMeasure = 0;
        }

        sb.append("<obj/"+"record/"+"STATS"+"> ");
    	sb.append("<Precision> ");
    	sb.append("\""+precision+"\".\n");

    	sb.append("<obj/"+"record/"+"STATS"+"> ");
    	sb.append("<Recall> ");
    	sb.append("\""+recall+"\".\n");
    	
    	sb.append("<obj/"+"record/"+"STATS"+"> ");
    	sb.append("<F-Measure> ");
    	sb.append("\""+fMeasure+"\".\n");
    	
    	sb.append("}\n }");
        String sparqlQueryString = sb.toString();

        //System.out.println(sparqlQueryString);
        UpdateRequest update  = UpdateFactory.create(sparqlQueryString);
        UpdateProcessor qexec = UpdateExecutionFactory.createRemote(update, endpointURL);
        qexec.execute();
        
    }
    
    public void printDetailedResultsToXML(List<EntityProfile> profilesD1, List<EntityProfile> profilesD2, String outputFile) throws FileNotFoundException {
        if (entityClusters.length == 0) {
            Log.warn("Empty set of equivalence clusters given as input!");
            return;
        }

        totalMatches = 0;
        final PrintWriter printWriter = new PrintWriter(new File(outputFile));

        printWriter.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
	    printWriter.println();

	    printWriter.println("<general>");
	    
        abstractDP.resetDuplicates();
        if (abstractDP instanceof BilateralDuplicatePropagation) { // Clean-Clean ER
            for (EquivalenceCluster cluster : entityClusters) {                
                if (cluster.getEntityIdsD1().size() != 1
                        || cluster.getEntityIdsD2().size() != 1) {
                    continue;
                }

                totalMatches++;

                final int entityId1 = cluster.getEntityIdsD1().get(0);
                final EntityProfile profile1 = profilesD1.get(entityId1);

                final int entityId2 = cluster.getEntityIdsD2().get(0);
                final EntityProfile profile2 = profilesD2.get(entityId2);

                final int originalDuplicates = abstractDP.getNoOfDuplicates();
                abstractDP.isSuperfluous(entityId1, entityId2);
                final int newDuplicates = abstractDP.getNoOfDuplicates();
                
            	printWriter.println();
            	
            	printWriter.println("<entity id=\""+cluster.toString()+"\">");

            	printWriter.print("<url1"+">");
            	printWriter.print(profile1.getEntityUrl().replace("&", "")+"");
            	printWriter.println("</url1>");
            	
            	printWriter.print("<url2"+">");
            	printWriter.print(profile2.getEntityUrl().replace("&", "")+"");
            	printWriter.println("</url2>");
            	
                
                printWriter.print("<pairType"+">");
                if (originalDuplicates == newDuplicates) {
                	printWriter.print("FP"); //false positive
                } else { // originalDuplicates < newDuplicates
                	printWriter.print("TP"); // true positive
                }               
            	printWriter.println("</pairType>");
            	
            	printWriter.print("<Profile1"+">");
            	printWriter.print((profile1+"").replace("&", ""));
            	printWriter.println("</Profile1>");
            	
            	printWriter.print("<Profile2"+">");
            	printWriter.print((profile2+"").replace("&", ""));
            	printWriter.println("</Profile2>");
                
            	printWriter.println("</entity>");
            }

            for (IdDuplicates duplicatesPair : abstractDP.getFalseNegatives()) {
                final EntityProfile profile1 = profilesD1.get(duplicatesPair.getEntityId1());
                final EntityProfile profile2 = profilesD2.get(duplicatesPair.getEntityId2());
            	printWriter.println();

                printWriter.println("<entity id=\""+duplicatesPair.toString()+"\">");

            	printWriter.print("<url1"+">");
            	printWriter.print(profile1.getEntityUrl().replace("&", "")+"");
            	printWriter.println("</url1>");
            	
            	printWriter.print("<url2"+">");
            	printWriter.print(profile2.getEntityUrl().replace("&", "")+"");
            	printWriter.println("</url2>");
            	
                
                printWriter.print("<pairType"+">");
            	printWriter.print("FN"); // false negative
            	printWriter.println("</pairType>");
            	
            	printWriter.print("<Profile1"+">");
            	printWriter.print((profile1+"").replace("&", ""));
            	printWriter.println("</Profile1>");
            	
            	printWriter.print("<Profile2"+">");
            	printWriter.print((profile2+"").replace("&", ""));
            	printWriter.println("</Profile2>");
                
            	printWriter.println("</entity>");
            }
        } else { // Dirty ER
            for (EquivalenceCluster cluster : entityClusters) {
                final int[] duplicatesArray = cluster.getEntityIdsD1().toArray();

                for (int i = 0; i < duplicatesArray.length; i++) {
                    for (int j = i + 1; j < duplicatesArray.length; j++) {
                        totalMatches++;

                        final EntityProfile profile1 = profilesD1.get(duplicatesArray[i]);
                        final EntityProfile profile2 = profilesD1.get(duplicatesArray[j]);

                        final int originalDuplicates = abstractDP.getNoOfDuplicates();
                        abstractDP.isSuperfluous(duplicatesArray[i], duplicatesArray[j]);
                        final int newDuplicates = abstractDP.getNoOfDuplicates();
                        
                    	printWriter.println();

                        printWriter.println("<entity id=\""+cluster.toString()+"\">");

                    	printWriter.print("<url1"+">");
                    	printWriter.print(profile1.getEntityUrl().replace("&", "")+"");
                    	printWriter.println("</url1>");
                    	
                    	printWriter.print("<url2"+">");
                    	printWriter.print(profile2.getEntityUrl().replace("&", "")+"");
                    	printWriter.println("</url2>");
                    	
                        
                        printWriter.print("<pairType"+">");
                        if (originalDuplicates == newDuplicates) {
                        	printWriter.print("FP"); //false positive
                        } else { // originalDuplicates < newDuplicates
                        	printWriter.print("TP"); // true positive
                        }               
                    	printWriter.println("</pairType>");
                    	
                    	printWriter.print("<Profile1"+">");
                    	printWriter.print((profile1+"").replace("&", ""));
                    	printWriter.println("</Profile1>");
                    	
                    	printWriter.print("<Profile2"+">");
                    	printWriter.print((profile2+"").replace("&", ""));
                    	printWriter.println("</Profile2>");
                        
                    	printWriter.println("</entity>");
                    }
                }
            }

            for (IdDuplicates duplicatesPair : abstractDP.getFalseNegatives()) {
                final EntityProfile profile1 = profilesD1.get(duplicatesPair.getEntityId1());
                final EntityProfile profile2 = profilesD1.get(duplicatesPair.getEntityId2());

            	printWriter.println();

                printWriter.println("<entity id=\""+duplicatesPair.toString()+"\">");

            	printWriter.print("<url1"+">");
            	printWriter.print(profile1.getEntityUrl().replace("&", "")+"");
            	printWriter.println("</url1>");
            	
            	printWriter.print("<url2"+">");
            	printWriter.print(profile2.getEntityUrl().replace("&", "")+"");
            	printWriter.println("</url2>");
            	
                
                printWriter.print("<pairType"+">");
            	printWriter.print("FN"); // false negative
            	printWriter.println("</pairType>");
            	
            	printWriter.print("<Profile1"+">");
            	printWriter.print((profile1+"").replace("&", ""));
            	printWriter.println("</Profile1>");
            	
            	printWriter.print("<Profile2"+">");
            	printWriter.print((profile2+"").replace("&", ""));
            	printWriter.println("</Profile2>");
                
            	printWriter.println("</entity>");
            }
            
        }

        if (0 < totalMatches) {
            precision = abstractDP.getNoOfDuplicates() / totalMatches;
        } else {
            precision = 0;
        }
        recall = ((double) abstractDP.getNoOfDuplicates()) / abstractDP.getExistingDuplicates();
        if (0 < precision && 0 < recall) {
            fMeasure = 2 * precision * recall / (precision + recall);
        } else {
            fMeasure = 0;
        }

    	printWriter.println();

        printWriter.println("<stats>");

        printWriter.print("<Precision"+">");
    	printWriter.print(precision+"");
    	printWriter.println("</Precision>");
    	
        printWriter.print("<Recall"+">");
    	printWriter.print(recall+"");
    	printWriter.println("</Recall>");
    	
        printWriter.print("<F-Measure"+">");
    	printWriter.print(fMeasure+"");
    	printWriter.println("</F-Measure>");
    	
        printWriter.println("</stats>");

    	printWriter.println();

    	printWriter.println("</general>");

        printWriter.close();
    }

    public void printDetailedResultsToDB(List<EntityProfile> profilesD1, List<EntityProfile> profilesD2, String dbURL) throws FileNotFoundException {
        if (entityClusters.length == 0) {
            Log.warn("Empty set of equivalence clusters given as input!");
            return;
        }

        totalMatches = 0;
        StringBuilder sb = new StringBuilder();
        String dbquery1 = "INSERT INTO "+ dbtable + " (url1, url2, pairtype, Profile1, Profile2) VALUES ";
        sb.append(dbquery1);

        abstractDP.resetDuplicates();
        if (abstractDP instanceof BilateralDuplicatePropagation) { // Clean-Clean ER
            for (EquivalenceCluster cluster : entityClusters) {                
                if (cluster.getEntityIdsD1().size() != 1
                        || cluster.getEntityIdsD2().size() != 1) {
                    continue;
                }

                totalMatches++;

                final int entityId1 = cluster.getEntityIdsD1().get(0);
                final EntityProfile profile1 = profilesD1.get(entityId1);

                final int entityId2 = cluster.getEntityIdsD2().get(0);
                final EntityProfile profile2 = profilesD2.get(entityId2);

                final int originalDuplicates = abstractDP.getNoOfDuplicates();
                abstractDP.isSuperfluous(entityId1, entityId2);
                final int newDuplicates = abstractDP.getNoOfDuplicates();
                
                sb.append("('"+profile1.getEntityUrl()+"', ");
                sb.append("'"+profile2.getEntityUrl()+"', ");
            	
                if (originalDuplicates == newDuplicates) {
                    sb.append("'"+"FP"+"', "); //false positive
                } else { // originalDuplicates < newDuplicates
                    sb.append("'"+"TP"+"', "); //true positive
                }
                sb.append("'"+profile1+"', ");
            	sb.append("'"+profile2+"'), ");
            }

            for (IdDuplicates duplicatesPair : abstractDP.getFalseNegatives()) {
                final EntityProfile profile1 = profilesD1.get(duplicatesPair.getEntityId1());
                final EntityProfile profile2 = profilesD2.get(duplicatesPair.getEntityId2());

                sb.append("('"+profile1.getEntityUrl()+"', ");
                sb.append("'"+profile2.getEntityUrl()+"', ");
                sb.append("'"+"FN"+"', "); // false negative
                sb.append("'"+profile1+"', ");
            	sb.append("'"+profile2+"'), ");
            }
        } else { // Dirty ER
            for (EquivalenceCluster cluster : entityClusters) {
                final int[] duplicatesArray = cluster.getEntityIdsD1().toArray();

                for (int i = 0; i < duplicatesArray.length; i++) {
                    for (int j = i + 1; j < duplicatesArray.length; j++) {
                        totalMatches++;

                        final EntityProfile profile1 = profilesD1.get(duplicatesArray[i]);
                        final EntityProfile profile2 = profilesD1.get(duplicatesArray[j]);

                        final int originalDuplicates = abstractDP.getNoOfDuplicates();
                        abstractDP.isSuperfluous(duplicatesArray[i], duplicatesArray[j]);
                        final int newDuplicates = abstractDP.getNoOfDuplicates();

                        sb.append("('"+profile1.getEntityUrl()+"', ");
                        sb.append("'"+profile2.getEntityUrl()+"', ");
                    	
                        if (originalDuplicates == newDuplicates) {
                            sb.append("'"+"FP"+"', "); //false positive
                        } else { // originalDuplicates < newDuplicates
                            sb.append("'"+"TP"+"', "); //true positive
                        }
                        sb.append("'"+profile1+"', ");
                    	sb.append("'"+profile2+"'), ");
                    }
                }
            }

            for (IdDuplicates duplicatesPair : abstractDP.getFalseNegatives()) {
                final EntityProfile profile1 = profilesD1.get(duplicatesPair.getEntityId1());
                final EntityProfile profile2 = profilesD1.get(duplicatesPair.getEntityId2());

                sb.append("('"+profile1.getEntityUrl()+"', ");
                sb.append("'"+profile2.getEntityUrl()+"', ");
                sb.append("'"+"FN"+"', "); // false negative
                sb.append("'"+profile1+"', ");
            	sb.append("'"+profile2+"'), ");
            }
        }

        if (0 < totalMatches) {
            precision = abstractDP.getNoOfDuplicates() / totalMatches;
        } else {
            precision = 0;
        }
        recall = ((double) abstractDP.getNoOfDuplicates()) / abstractDP.getExistingDuplicates();
        if (0 < precision && 0 < recall) {
            fMeasure = 2 * precision * recall / (precision + recall);
        } else {
            fMeasure = 0;
        }

        sb.append("('"+precision+"', ");
        sb.append("'"+recall+"', ");
        sb.append("'"+fMeasure+"', ");
        sb.append("'"+"NULL"+"', ");
    	sb.append("'"+"NULL"+"'); ");

    	String dbquery = sb.toString();

    	try {
            if (dbuser == null) {
                Log.error("Database user has not been set!");
            }
            if (dbpassword == null) {
                Log.error("Database password has not been set!");
            }
            if (dbtable == null) {
                Log.error("Database table has not been set!");
            }


            Connection conn = null;
            if (dbURL.startsWith("mysql")) {
                conn = getMySQLconnection(dbURL);
            } else if (dbURL.startsWith("postgresql")) {
                conn = getPostgreSQLconnection(dbURL);
            } else {
                Log.error("Only MySQL and PostgreSQL are supported for the time being!");
            }


            final Statement stmt = conn.createStatement();
            stmt.executeQuery(dbquery);//retrieve the appropriate table
            } catch (Exception ex) {
                Log.error("Error in db writing!", ex);
            }
    }
    
    public void setStatistics() {
        if (entityClusters.length == 0) {
            Log.warn("Empty set of equivalence clusters given as input!");
            return;
        }

        totalMatches = 0;
        if (abstractDP instanceof BilateralDuplicatePropagation) { // Clean-Clean ER
            for (EquivalenceCluster cluster : entityClusters) {
                for (TIntIterator outIterator = cluster.getEntityIdsD1().iterator(); outIterator.hasNext();) {
                    int entityId1 = outIterator.next();
                    for (TIntIterator inIterator = cluster.getEntityIdsD2().iterator(); inIterator.hasNext();) {
                        totalMatches++;
                        Comparison comparison = new Comparison(true, entityId1, inIterator.next());
                        abstractDP.isSuperfluous(entityId1, inIterator.next());
                    }
                }
            }
        } else { // Dirty ER
            for (EquivalenceCluster cluster : entityClusters) {
                final int[] duplicatesArray = cluster.getEntityIdsD1().toArray();

                for (int i = 0; i < duplicatesArray.length; i++) {
                    for (int j = i + 1; j < duplicatesArray.length; j++) {
                        totalMatches++;
                        Comparison comparison = new Comparison(false, duplicatesArray[i], duplicatesArray[j]);
                        abstractDP.isSuperfluous(duplicatesArray[i], duplicatesArray[j]);
                    }
                }
            }
        }

        if (0 < totalMatches) {
            precision = abstractDP.getNoOfDuplicates() / totalMatches;
        } else {
            precision = 0;
        }
        recall = ((double) abstractDP.getNoOfDuplicates()) / abstractDP.getExistingDuplicates();
        if (0 < precision && 0 < recall) {
            fMeasure = 2 * precision * recall / (precision + recall);
        } else {
            fMeasure = 0;
        }
    }
}
