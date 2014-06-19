package edu.umass.csiesl.mimno;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.Iterator;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Pattern;

import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.CharSequenceLowercase;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.TokenSequence2FeatureSequenceWithBigrams;
import cc.mallet.pipe.TokenSequenceNGrams;
import cc.mallet.pipe.TokenSequenceRemoveStopwords;
import cc.mallet.pipe.iterator.CsvIterator;
import cc.mallet.topics.ParallelTopicModel;
import cc.mallet.topics.TopicInferencer;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.IDSorter;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelSequence;

public class Case3 {
	
	public String fileName = "C:/Users/Sultan/Documents/research/Paper1/Solvakia/Slovakia_L/CombinedL/singeFileSolvak.txt";
	//public String fileName = "C:/mallet-2.0.7/mallet-2.0.7/Solvakia_results/singeFileArabicHasm.txt";
	public String dirName = "C:/Users/Sultan/Documents/research/Paper1/Solvakia/Slovakia_L/CombinedL";
	//public String dirName = "C:/Users/Sultan/Documents/Research/SaudiArabia/ArabictexyOnly/texyOnly";
	public String stopwordsFileName = "C:/mallet-2.0.7/mallet-2.0.7/stoplists/stopwords_slovak.txt";
	//public String stopwordsFileName = "C:/mallet-2.0.7/mallet-2.0.7/stoplistsstopwords_Arabic.txt";
	public void run()
	{
		
		//First convet data to a singe file txt having all instances.
		
		// First: The file you want to keep all txt files in it "mallet format"...
		try {
			create_single_file(fileName,dirName);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		//Second Pass the file to an identical class that mimic the second one:
		try {
			topicModeling();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void create_single_file(String fileName, String dirName) throws IOException
	{


		PrintWriter out;


		out = new PrintWriter( new FileWriter(fileName));




		File folder = new File(dirName);
		for (File fileEntry : folder.listFiles()) {
			if (fileEntry.isDirectory()== false) {
				InputStreamReader is;
				is = new InputStreamReader(new FileInputStream(fileEntry));
				StringBuffer txtStr = new StringBuffer();
				int c;
				while ((c = is.read()) != -1) {
					if((char)c == '\n')
						txtStr.append(' ');
					else
						txtStr.append((char)c);
				}
				out.println(fileEntry.getName()+" X    "+ txtStr.toString());
			}
		}
		out.close();
	}
	
	public  void topicModeling() throws Exception {

    	String thefileName = fileName;       //"C:/Users/Sultan/Downloads/malletstuff/ap.txt";
        // Begin by importing documents from text to feature sequences
        ArrayList<Pipe> pipeList = new ArrayList<Pipe>();
        
        // Pipes: lowercase, tokenize, remove stopwords, map to features
        int[] sizes = {1};
        String delim = " ";
        pipeList.add( new CharSequenceLowercase() );
        pipeList.add( new CharSequence2TokenSequence(Pattern.compile("\\p{L}[\\p{L}\\p{P}]+\\p{L}")) );
        pipeList.add( new TokenSequenceRemoveStopwords(new File(stopwordsFileName), "UTF-8", false, false, false) );
        pipeList.add(new TokenSequenceNGrams(sizes));
       // pipeList.add( new TokenSequence2FeatureSequence() );
       
        pipeList.add( new TokenSequence2FeatureSequenceWithBigrams() );
        InstanceList instances = new InstanceList (new SerialPipes(pipeList));

        Reader fileReader = new InputStreamReader(new FileInputStream(new File(thefileName)), "UTF-8");
        instances.addThruPipe(new CsvIterator (fileReader, Pattern.compile("^(\\S*)[\\s,]*(\\S*)[\\s,]*(.*)$"),3, 2, 1)); // data, label, name fields

        // Create a model with 100 topics, alpha_t = 0.01, beta_w = 0.01
        //  Note that the first parameter is passed as the sum over topics, while
        //  the second is the parameter for a single dimension of the Dirichlet prior.
        int numTopics = 10;
        ParallelTopicModel model = new ParallelTopicModel(numTopics, 1.0, 0.01);

        model.addInstances(instances);

        // Use two parallel samplers, which each look at one half the corpus and combine
        //  statistics after every iteration.
        model.setNumThreads(2);

        // Run the model for 50 iterations and stop (this is for testing only, 
        //  for real applications, use 1000 to 2000 iterations)
        model.setNumIterations(2000);
        model.estimate();

        // Show the words and topics in the first instance

        // The data alphabet maps word IDs to strings
        Alphabet dataAlphabet = instances.getDataAlphabet();
        
        FeatureSequence tokens = (FeatureSequence) model.getData().get(0).instance.getData();
        LabelSequence topics = model.getData().get(0).topicSequence;
        
        Formatter out = new Formatter(new StringBuilder(), Locale.US);
        for (int position = 0; position < tokens.getLength(); position++) {
            out.format("%s-%d ", dataAlphabet.lookupObject(tokens.getIndexAtPosition(position)), topics.getIndexAtPosition(position));
        }
        System.out.println(out);
        
        // Estimate the topic distribution of the first instance, 
        //  given the current Gibbs state.
        double[] topicDistribution = model.getTopicProbabilities(0);

        // Get an array of sorted sets of word ID/count pairs
        ArrayList<TreeSet<IDSorter>> topicSortedWords = model.getSortedWords();
        
        // Show top 5 words in topics with proportions for the first document
        for (int topic = 0; topic < numTopics; topic++) {
            Iterator<IDSorter> iterator = topicSortedWords.get(topic).iterator();
            
            out = new Formatter(new StringBuilder(), Locale.US);
            out.format("%d\t%.3f\t", topic, topicDistribution[topic]);
            int rank = 0;
            while (iterator.hasNext() && rank < 5) {
                IDSorter idCountPair = iterator.next();
                out.format("%s (%.0f) ", dataAlphabet.lookupObject(idCountPair.getID()), idCountPair.getWeight());
                rank++;
            }
            System.out.println(out);
        }
        
        // Create a new instance with high probability of topic 0
        StringBuilder topicZeroText = new StringBuilder();
        Iterator<IDSorter> iterator = topicSortedWords.get(0).iterator();

        int rank = 0;
        while (iterator.hasNext() && rank < 5) {
            IDSorter idCountPair = iterator.next();
            topicZeroText.append(dataAlphabet.lookupObject(idCountPair.getID()) + " ");
            rank++;
        }

        // Create a new instance named "test instance" with empty target and source fields.
        InstanceList testing = new InstanceList(instances.getPipe());
        testing.addThruPipe(new Instance(topicZeroText.toString(), null, "test instance", null));

        TopicInferencer inferencer = model.getInferencer();
        double[] testProbabilities = inferencer.getSampledDistribution(testing.get(0), 10, 1, 5);
        System.out.println("0\t" + testProbabilities[0]);
    }
}
