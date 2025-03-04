package edu.stanford.nlp.hcoref.md;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.hcoref.CorefProperties;
import edu.stanford.nlp.hcoref.data.Dictionaries;
import edu.stanford.nlp.hcoref.data.Mention;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.IntPair;

public class HybridCorefMentionFinder extends CorefMentionFinder {

  public MentionDetectionClassifier mdClassifier = null;

  public HybridCorefMentionFinder(HeadFinder headFinder, Properties props) throws ClassNotFoundException, IOException {
    this.headFinder = headFinder;
    this.lang = CorefProperties.getLanguage(props);
    mdClassifier = (CorefProperties.isMentionDetectionTraining(props))?
        null : IOUtils.readObjectFromURLOrClasspathOrFileSystem(CorefProperties.getMentionDetectionModel(props));
  }

  @Override
  public List<List<Mention>> findMentions(Annotation doc, Dictionaries dict, Properties props) {
    List<List<Mention>> predictedMentions = new ArrayList<List<Mention>>();
    Set<String> neStrings = Generics.newHashSet();
    List<Set<IntPair>> mentionSpanSetList = Generics.newArrayList();
    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
//    boolean useNewMD = Boolean.parseBoolean(props.getProperty("useNewMD", "false"));

    // extract premarked mentions, NP/PRP, named entity, enumerations
    for (CoreMap s : sentences) {
      List<Mention> mentions = new ArrayList<Mention>();
      predictedMentions.add(mentions);
      Set<IntPair> mentionSpanSet = Generics.newHashSet();
      Set<IntPair> namedEntitySpanSet = Generics.newHashSet();

      extractPremarkedEntityMentions(s, mentions, mentionSpanSet, namedEntitySpanSet);
      extractNamedEntityMentions(s, mentions, mentionSpanSet, namedEntitySpanSet);
      extractNPorPRP(s, mentions, mentionSpanSet, namedEntitySpanSet);
      extractEnumerations(s, mentions, mentionSpanSet, namedEntitySpanSet);

      addNamedEntityStrings(s, neStrings, namedEntitySpanSet);
      mentionSpanSetList.add(mentionSpanSet);
    }
    extractNamedEntityModifiers(sentences, mentionSpanSetList, predictedMentions, neStrings);

    // find head
    for(int i=0 ; i<sentences.size() ; i++ ) {
      findHead(sentences.get(i), predictedMentions.get(i));
    }

    // mention selection based on document-wise info
    removeSpuriousMentions(doc, predictedMentions, dict, CorefProperties.removeNested(props), lang);

    // if this is for MD training, skip classification
    if(!CorefProperties.isMentionDetectionTraining(props)) {
      mdClassifier.classifyMentions(predictedMentions, dict, props);
    }

    return predictedMentions;
  }

  protected static void extractNamedEntityMentions(CoreMap s, List<Mention> mentions, Set<IntPair> mentionSpanSet, Set<IntPair> namedEntitySpanSet) {
    List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
    SemanticGraph basicDependency = s.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    SemanticGraph collapsedDependency = s.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
    String preNE = "O";
    int beginIndex = -1;
    for(CoreLabel w : sent) {
      String nerString = w.ner();
      if(!nerString.equals(preNE)) {
        int endIndex = w.get(CoreAnnotations.IndexAnnotation.class) - 1;
        if(!preNE.matches("O")){
          if(w.get(CoreAnnotations.TextAnnotation.class).equals("'s") && w.tag().equals("POS")) {
            endIndex++;
          }
          IntPair mSpan = new IntPair(beginIndex, endIndex);
          // Need to check if beginIndex < endIndex because, for
          // example, there could be a 's mislabeled by the NER and
          // attached to the previous NER by the earlier heuristic
          if(beginIndex < endIndex && !mentionSpanSet.contains(mSpan)) {
            int dummyMentionId = -1;
            Mention m = new Mention(dummyMentionId, beginIndex, endIndex, sent, basicDependency, collapsedDependency, new ArrayList<CoreLabel>(sent.subList(beginIndex, endIndex)));
            mentions.add(m);
            mentionSpanSet.add(mSpan);
            namedEntitySpanSet.add(mSpan);
          }
        }
        beginIndex = endIndex;
        preNE = nerString;
      }
    }
    // NE at the end of sentence
    if(!preNE.matches("O")) {
      IntPair mSpan = new IntPair(beginIndex, sent.size());
      if(!mentionSpanSet.contains(mSpan)) {
        int dummyMentionId = -1;
        Mention m = new Mention(dummyMentionId, beginIndex, sent.size(), sent, basicDependency, collapsedDependency, new ArrayList<CoreLabel>(sent.subList(beginIndex, sent.size())));
        mentions.add(m);
        mentionSpanSet.add(mSpan);
        namedEntitySpanSet.add(mSpan);
      }
    }
  }

  /** Filter out all spurious mentions  */
  @Override
  public void removeSpuriousMentionsEn(Annotation doc, List<List<Mention>> predictedMentions, Dictionaries dict) {

    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);

    for(int i=0 ; i < predictedMentions.size() ; i++) {
      CoreMap s = sentences.get(i);
      List<Mention> mentions = predictedMentions.get(i);

      List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
      Set<Mention> remove = Generics.newHashSet();

      for(Mention m : mentions){
        String headPOS = m.headWord.get(CoreAnnotations.PartOfSpeechAnnotation.class);

        // non word such as 'hmm'
        if(dict.nonWords.contains(m.headString)) remove.add(m);

        // adjective form of nations
        // the [American] policy -> not mention
        // speak in [Japanese] -> mention
        // check if the mention is noun and the next word is not noun
        if (dict.isAdjectivalDemonym(m.spanToString())) {
          if(!headPOS.startsWith("N")
              || (m.endIndex < sent.size() && sent.get(m.endIndex).tag().startsWith("N")) ) {
            remove.add(m);
          }
        }

        // stop list (e.g., U.S., there)
        if (inStopList(m)) remove.add(m);
      }
      mentions.removeAll(remove);
    }
  }

  private static void extractNPorPRP(CoreMap s, List<Mention> mentions, Set<IntPair> mentionSpanSet, Set<IntPair> namedEntitySpanSet) {
    List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
    Tree tree = s.get(TreeCoreAnnotations.TreeAnnotation.class);
    tree.indexLeaves();
    SemanticGraph basicDependency = s.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    SemanticGraph collapsedDependency = s.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);

    TregexPattern tgrepPattern = npOrPrpMentionPattern;
    TregexMatcher matcher = tgrepPattern.matcher(tree);
    while (matcher.find()) {
      Tree t = matcher.getMatch();
      List<Tree> mLeaves = t.getLeaves();
      int beginIdx = ((CoreLabel)mLeaves.get(0).label()).get(CoreAnnotations.IndexAnnotation.class)-1;
      int endIdx = ((CoreLabel)mLeaves.get(mLeaves.size()-1).label()).get(CoreAnnotations.IndexAnnotation.class);
      if (",".equals(sent.get(endIdx-1).word())) { endIdx--; } // try not to have span that ends with ,
      IntPair mSpan = new IntPair(beginIdx, endIdx);
//      if(!mentionSpanSet.contains(mSpan) && (!insideNE(mSpan, namedEntitySpanSet)) ) {
      if(!mentionSpanSet.contains(mSpan) && (!insideNE(mSpan, namedEntitySpanSet) || t.value().startsWith("PRP")) ) {
        int dummyMentionId = -1;
        Mention m = new Mention(dummyMentionId, beginIdx, endIdx, sent, basicDependency, collapsedDependency, new ArrayList<CoreLabel>(sent.subList(beginIdx, endIdx)), t);
        mentions.add(m);
        mentionSpanSet.add(mSpan);

        if(m.originalSpan.size() > 1) {
          boolean isNE = true;
          for(CoreLabel cl : m.originalSpan) {
            if(!cl.tag().startsWith("NNP")) isNE = false;
          }
          if(isNE) {
            namedEntitySpanSet.add(mSpan);
          }
        }
      }
    }
  }

}
