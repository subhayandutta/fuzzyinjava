package com.sd.fuzzy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystems;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.AutomatonQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.LevenshteinAutomata;

// Ref: https://lucene.apache.org/core/8_0_0/demo/src-html/org/apache/lucene/demo/IndexFiles.html
// Ref: https://lucene.apache.org/core/8_0_0/demo/src-html/org/apache/lucene/demo/SearchFiles.html
public class FuzzyTest {
	/**
	 * The location where the index files will be stored
	 */
	private static String indexDirFirstName = "./Lucene/FirstN/Index";
	
	/**
	 * The location of the File which contains the first names
	 */
	private static String dataDirFirstName = "./Lucene/FirstN/Data";

	/**
	 * The tag with which to store the data in the index
	 */
	private static final String FIRST_NAME = "FirstName";
	
	/**
	 * If you want to recreate the index or add something to the index instead of deleting the index and creating again, you can use this boolean
	 */
	private static boolean indexBuilt = false;
	
	/**
	 * Map of matched strings and %cent match
	 */
	private static ConcurrentHashMap<String, Double> matchedFirstNameVsPercentage = new ConcurrentHashMap<>();

	/**
	 * Go through the files that are present in dataDirFirstName and create the index of First names
	 * @throws IOException
	 */
	public static void buildFirstNameIndex() throws IOException {
		Analyzer analyzer = new StandardAnalyzer();
		IndexWriterConfig config = new IndexWriterConfig(analyzer);
		// indexDir = new FSDirectory
		IndexWriter indexWriter = new IndexWriter(
				FSDirectory.open(FileSystems.getDefault().getPath(indexDirFirstName)), config);
		File dir = new File(dataDirFirstName);
		File[] files = dir.listFiles();

		for (File file : files) {
			BufferedReader buf = new BufferedReader(new FileReader(file));
			String line = "";
			while ((line = buf.readLine()) != null) {
				Document doc = new Document();
				doc.add(new TextField(FIRST_NAME, line, Field.Store.YES));
				indexWriter.addDocument(doc);
			}
			buf.close();
		}
		indexBuilt = true;
		indexWriter.close();
	}

	/**
	 * Search the first name once the index is built.
	 * @param searchString - The string to search, example Camellia
	 * @throws IOException
	 * @throws ParseException
	 */
	public static void searchFNames(String searchString) throws IOException, ParseException {
		if (!indexBuilt) {
			System.out.println("Building all indexex for first time");
			long startTime = System.currentTimeMillis();
			System.out.println("Building first name index for first time");
			buildFirstNameIndex();
			long endTime = System.currentTimeMillis();
			System.out.println("All indexes built for first time. Time taken: " + (endTime - startTime));
			indexBuilt = true;
		}
		System.out.println("Searching for '" + searchString + "'");
		DirectoryReader ireader = DirectoryReader.open(FSDirectory.open(FileSystems.getDefault().getPath(indexDirFirstName)));
		IndexSearcher isearcher = new IndexSearcher(ireader);
		// Parse a simple query that searches for "text":

		TopDocs tophits = null;
		matchedFirstNameVsPercentage.clear();

		Term term = new Term(FIRST_NAME, searchString);
		Automaton fuzzyAutomation =  new LevenshteinAutomata(searchString, indexBuilt).toAutomaton(2);
		AutomatonQuery query = new AutomatonQuery(term, fuzzyAutomation);
		tophits = isearcher.search(query, 1000);
		getResults(tophits, isearcher, searchString, "AutomatonQuery");
		
		printMatches();
		ireader.close();
	}

	/**
	 * A convenient way to display the matched results along with the query used for the search
	 * @param tophits - The top hits from the query
	 * @param isearcher - The Searcher to use
	 * @param searchString - The String which is searched
	 * @param queryName - The name of the query used. In this example Automation query
	 * @throws IOException
	 */
	private static void getResults(TopDocs tophits, IndexSearcher isearcher, String searchString, String queryName) throws IOException {
		for (ScoreDoc scoreDoc : tophits.scoreDocs) {
			Document doc = isearcher.doc(scoreDoc.doc);
			if (matchedFirstNameVsPercentage.containsKey(doc.get(FIRST_NAME)))
				continue;
			double percentMatch = 100 * similarity(searchString, doc.get(FIRST_NAME));
			System.out.println(queryName + " Found : " + doc.get(FIRST_NAME) + " percentMatch: " + percentMatch);
			if ( matchedFirstNameVsPercentage.containsKey(doc.get(FIRST_NAME)) ) {
				double percentVal = matchedFirstNameVsPercentage.get(doc.get(FIRST_NAME));
				if ( doc.get(FIRST_NAME).trim().equalsIgnoreCase(searchString) ) {
					percentVal = 100;
				}
				matchedFirstNameVsPercentage.put(doc.get(FIRST_NAME), percentVal);
			}
			else {
				double percentVal = percentMatch;
				try {
					percentVal = matchedFirstNameVsPercentage.get(doc.get(FIRST_NAME));
					if ( doc.get(FIRST_NAME).trim().equalsIgnoreCase(searchString) ) {
						percentVal = 100;
					}
				} catch (NullPointerException npe) {
				}
				matchedFirstNameVsPercentage.put(doc.get(FIRST_NAME), percentVal);
			}
		}
	}

	//Ref: https://stackoverflow.com/questions/47905195/how-to-do-percentage-match-between-list-of-strings-in-apache-lucene/48628326
	private static double similarity(String s1, String s2) {
		String longer = s1, shorter = s2;
		if (s1.length() < s2.length()) { // longer should always have greater length
			longer = s2; shorter = s1;
		}
		int longerLength = longer.length();
		if (longerLength == 0) { return 1.0; /* both strings are zero length */ }
		/* // If you have Apache Commons Text 
		     // you can use it to calculate the edit distance:
		    LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
		    return (longerLength - levenshteinDistance.apply(longer, shorter)) / (double) longerLength; */
		return (double)(longerLength - editDistance(longer, shorter)) / (double) longerLength;

	}

	private static int editDistance(String s1, String s2) {
		s1 = s1.toLowerCase();
		s2 = s2.toLowerCase();

		int[] costs = new int[s2.length() + 1];
		for (int i = 0; i <= s1.length(); i++) {
			int lastValue = i;
			for (int j = 0; j <= s2.length(); j++) {
				if (i == 0)
					costs[j] = j;
				else {
					if (j > 0) {
						int newValue = costs[j - 1];
						if (s1.charAt(i - 1) != s2.charAt(j - 1))
							newValue = Math.min(Math.min(newValue, lastValue),
									costs[j]) + 1;
						costs[j - 1] = lastValue;
						lastValue = newValue;
					}
				}
			}
			if (i > 0)
				costs[s2.length()] = lastValue;
		}
		return costs[s2.length()];
	}

	/**
	 * Convenient function to sort the data by value, highest value first
	 * @param hm
	 * @return
	 */
	private static HashMap<String, Double> sortByValue(ConcurrentHashMap<String, Double> hm) { 
        // Create a list from elements of HashMap 
        List<Map.Entry<String, Double> > list = 
        		new LinkedList<Map.Entry<String, Double> >(hm.entrySet()); 
  
        // Sort the list 
        Collections.sort(list, new Comparator<Map.Entry<String, Double> >() { 
            public int compare(Map.Entry<String, Double> o1,  
                               Map.Entry<String, Double> o2) 
            { 
                return (o2.getValue()).compareTo(o1.getValue()); 
            } 
        }); 
          
        // put data from sorted list to hashmap  
        HashMap<String, Double> temp = new LinkedHashMap<String, Double>(); 
        for (Map.Entry<String, Double> aa : list) { 
            temp.put(aa.getKey(), aa.getValue()); 
        } 
        return temp; 
    } 
	
	private static void printMatches() {		
		Map<String, Double> hm1 = sortByValue(matchedFirstNameVsPercentage); 
		  
        // print the sorted hashmap 
        for (Map.Entry<String, Double> en : hm1.entrySet()) { 
            System.out.println("Key = " + en.getKey() +  
                          ", Value = " + en.getValue()); 
        }         
	}

	public static void main( String [] args ) throws Exception {
		buildFirstNameIndex();
		for(;;) {
			System.out.println("Enter name to search:");
			BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			String searchString = "";
			while( ( searchString = reader.readLine() ) != null )
				searchFNames(searchString);
		}
	}
}
