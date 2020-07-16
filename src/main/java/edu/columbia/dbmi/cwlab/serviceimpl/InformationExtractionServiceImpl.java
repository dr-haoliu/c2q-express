package edu.columbia.dbmi.cwlab.serviceimpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import org.apache.log4j.Logger;

import edu.columbia.dbmi.cwlab.pojo.DisplayCriterion;
import edu.columbia.dbmi.cwlab.pojo.Document;
import edu.columbia.dbmi.cwlab.pojo.GlobalSetting;
import edu.columbia.dbmi.cwlab.pojo.Paragraph;
import edu.columbia.dbmi.cwlab.pojo.Sentence;
import edu.columbia.dbmi.cwlab.pojo.Term;

import edu.columbia.dbmi.cwlab.tool.CoreNLP;
import edu.columbia.dbmi.cwlab.tool.FeedBackTool;
import edu.columbia.dbmi.cwlab.tool.LogicAnalysisTool;
import edu.columbia.dbmi.cwlab.tool.NERTool;
import edu.columbia.dbmi.cwlab.tool.NegReTool;
import edu.columbia.dbmi.cwlab.tool.RelExTool;
import edu.stanford.nlp.util.Triple;
import net.sf.json.JSONObject;


public class InformationExtractionServiceImpl {
	
	private static Logger logger = Logger.getLogger(FeedBackTool.class);
	
	CoreNLP corenlp = new CoreNLP();
	NERTool nertool = new NERTool();
	NegReTool negtool = new NegReTool();
	LogicAnalysisTool logictool = new LogicAnalysisTool();
	RelExTool reltool = new RelExTool();

	public Paragraph translateText(String freetext, boolean include) {
		// TODO Auto-generated method stub
		return null;
	}


	public Document runIE4Doc(Document doc) {
		// TODO Auto-generated method stub
		return null;
	}


	public Document translateByDoc(String initial_event, String inclusion_criteria, String exclusion_criteria) {
		Document doc = new Document();
		doc.setInitial_event(translateByBlock(initial_event));
		doc.setInclusion_criteria(translateByBlock(inclusion_criteria));
		doc.setExclusion_criteria(translateByBlock(exclusion_criteria));
		return doc;
	}


	public List<Paragraph> translateByBlock(String text) {
		String[] pas = text.split("\n");
		List<Paragraph> spas = new ArrayList<Paragraph>();
		if (text.length() == 0) {
			return spas;
		}
		int pairs=0;
		for (String p : pas) {
			Paragraph pa = new Paragraph();
			List<String> block_text = corenlp.splitParagraph(p);
			List<Sentence> sents = new ArrayList<Sentence>();
			// NER, relation, negation, logic are operated against sentence
			// level
			for (String s : block_text) {
				// filter bracket
				s = s.replaceAll("-LRB-", "(");
				s = s.replaceAll("-RRB-", ")");
				s = s.replaceAll("-LSB-", "[");
				s = s.replaceAll("-RSB-", "]");
				s = s.replaceAll("-LCB-", "{");
				s = s.replaceAll("-RCB-", "}");
				Sentence sent = new Sentence(" "+s+" ");
				String crf_results=sent.getText();
				if(s.trim().split(" ").length<3){
					crf_results=nertool.nerByDicLookUp(sent.getText());
				}
				
				if(crf_results.length()<=sent.getText().length()){
					crf_results = nertool.nerByCrf(sent.getText());
				}
				//System.out.println("crf_results="+crf_results);
				List<Term> terms = nertool.formulateNerResult(sent.getText(), crf_results);
				
				//Aho–Corasick for rule-based screening
				try{
				
					terms=nertool.nerEnhancedByACAlgorithm(sent.getText(),terms);
				
				}catch(Exception e){
					
				}
				//System.out.println("===> after enhanced ====>");
				
				terms=patchTermLevel(terms);
				String display="";
				try{
					display = nertool.trans4display(sent.getText(),terms);
				}catch(Exception ex){
					
				}
				//String display = nertool.trans2Html(crf_results);			
				// displaying
				sent.setTerms(terms);
				sent.setDisplay(display);
				List<Term> primary_entities = new ArrayList<Term>();
				List<Term> attributes = new ArrayList<Term>();
				// Separate primary terms and attributes
				for (Term t : terms) {
					if (Arrays.asList(GlobalSetting.primaryEntities).contains(t.getCategorey())) {
						// Negation detection
						boolean ntag = negtool.negReg(sent.getText(), t.getText(), terms);
						t.setNeg(ntag);
						primary_entities.add(t);

					} else if (Arrays.asList(GlobalSetting.atrributes).contains(t.getCategorey())) {
						attributes.add(t);
					}
				}
				List<Term> allterms = new ArrayList<Term>();
				allterms.addAll(primary_entities);
				allterms.addAll(attributes);
				sent.setTerms(allterms);
				List<Triple<Integer, Integer, String>> relations = new ArrayList<Triple<Integer, Integer, String>>();
				for (Term t : primary_entities) {
					for (Term a : attributes) {
						// relation extraction
						// It is good to reuse by "String" rather than relation
						// id or something
						pairs++;
						String rel = "no_relation";
						boolean relflag = false;
						//reltool.getshortestDepPath(t, a, sent.getText(), corenlp) < 1000
						if (logictool.isConnected(t, a, sent.getText())) {
							relflag = true;
						}
						//relflag = true;
						if (relflag == true && a.getCategorey().equals("Value")) {
							rel = "has_value";
						}
						if (relflag == true && a.getCategorey().equals("Temporal")) {
							rel = "has_temporal";
						}
						Triple<Integer, Integer, String> triple = new Triple<Integer, Integer, String>(t.getTermId(),
								a.getTermId(), rel);
						if (triple.third().equals("no_relation") == false) {
							relations.add(triple);
						}
					}
				}
				//relation revision
				relations=reltool.relsRevision(allterms,relations);
				sent.setRelations(relations);
				
				long startTime = System.currentTimeMillis();
				List<LinkedHashSet<Integer>> logic_groups =logictool.ddep(sent.getText(), primary_entities);
				long endTime = System.currentTimeMillis();
				//System.out.println("Time consuming:"+(endTime-startTime));
				sent.setLogic_groups(logic_groups);
				sents.add(sent);
			}
			pa.setSents(sents);
			logger.info(JSONObject.fromObject(pa));
			spas.add(pa);
		}
		//System.out.println("pair count="+pairs);
		
		return spas;
	}

	
	public List<DisplayCriterion> displayDoc(List<Paragraph> ps) {
		// TODO Auto-generated method stub
		List<DisplayCriterion> displaycriteria = new ArrayList<DisplayCriterion>();
		int i = 1;
		for (Paragraph p : ps) {
			boolean ehrstatus = false;
			DisplayCriterion d = new DisplayCriterion();
			StringBuffer sb = new StringBuffer();
			for (Sentence s : p.getSents()) {
				sb.append(s.getDisplay());
				for (Term t : s.getTerms()) {
					if (Arrays.asList(GlobalSetting.primaryEntities).contains(t.getCategorey())) {
						ehrstatus = true;
					}
				}
			}
			d.setCriterion(sb.toString());
			d.setId(i++);
			d.setEhrstatus(ehrstatus);
			displaycriteria.add(d);
		}
		return displaycriteria;
	}

	
	public Document patchIEResults(Document doc) {
		// TODO Auto-generated method stub
		if (doc.getInitial_event() != null) {
			List<Paragraph> originalp = doc.getInitial_event();
			originalp = patchDocLevel(originalp);
			doc.setInitial_event(originalp);
		}
		if (doc.getInclusion_criteria() != null) {
			List<Paragraph> originalp = doc.getInclusion_criteria();
			originalp = patchDocLevel(originalp);
			doc.setInclusion_criteria(originalp);
		}
		if (doc.getExclusion_criteria() != null) {
			List<Paragraph> originalp = doc.getExclusion_criteria();
			originalp = patchDocLevel(originalp);
			doc.setExclusion_criteria(originalp);
		}
		return doc;
	}
	
	public List<Term> patchTermLevel(List<Term> terms){
		for(int i=0;i<terms.size();i++){
			List<String> lemmas = corenlp.getLemmasList(terms.get(i).getText());
			if ((lemmas.contains("day") || lemmas.contains("month") || lemmas.contains("year"))&&(lemmas.contains("old")==false)&&(lemmas.contains("/")==false)) {
				if(i>0 && terms.get(i-1).getCategorey().equals("Demographic")==false){
					terms.get(i).setCategorey("Temporal");
				}
				
			}
			if(terms.get(i).getText().endsWith("therapy")&&terms.get(i).getText().contains(" ")==false){
				terms.get(i).setCategorey("Procedure");
			}
			String text=terms.get(i).getText();
			if (text.endsWith(")")&& text.contains("(")==false){
				//System.out.println("======>"+text);
				int end_index=terms.get(i).getEnd_index();
				terms.get(i).setText(text.substring(0, text.length()-1));
				terms.get(i).setEnd_index(end_index-1);
			}
		}
		return terms;
	}
	

	// term-level calibration
	public List<Paragraph> patchDocLevel(List<Paragraph> originalp) {
		for (Paragraph p : originalp) {
			if (p.getSents() != null) {
				for (Sentence s : p.getSents()) {
					if (s.getTerms() != null) {
						for (int i = 0; i < s.getTerms().size(); i++) {
							if (s.getTerms().get(i).getCategorey().equals("Value")) {
								String text = s.getTerms().get(i).getText();
								List<String> lemmas = corenlp.getLemmasList(text);
								if (lemmas.contains("old") || lemmas.contains("young") || lemmas.contains("older")
										|| lemmas.contains("younger")) {
									// if there is no age in this sentence.
									if (hasDemoAge(s.getTerms())==false) {
										Term t = new Term();
										t.setCategorey("Demographic");
										t.setStart_index(-1);
										t.setEnd_index(-1);
										t.setNeg(false);
										t.setText("age");
										Integer assignId = s.getTerms().size();
										t.setTermId(assignId);
										s.getTerms().add(t);
										s.getRelations().add(new Triple<Integer, Integer, String>(assignId,
												s.getTerms().get(i).getTermId(), "has_value"));
									}
								}
							}
							
							
							
						}
					}
				}
			}
		}
		return originalp;
	}

	public boolean hasDemoAge(List<Term> terms) {
		for (Term t : terms) {
			List<String> lemmas = corenlp.getLemmasList(t.getText());
			if (lemmas.get(0).equals("age")) {
				return true;
			}
		}
		return false;
	}

	
	

	public boolean isAcronym(String word) {
		// if one is less than three letters.
		if (word.length() < 3) {
			return true;
		} else {
			if (word.indexOf(" ") == -1) {
				for (int i = 0; i < word.length(); i++) {
					if (Character.isDigit(word.charAt(i))) {
						return true;
					}
				}
			}
			// if all upper case
			if (Character.isUpperCase(word.charAt(1))) {
				return true;
			}
		}
		// if there is a number in the word

		return false;
	}


	public Document abbrExtensionByDoc(Document doc) {
		// TODO Auto-generated method stub
		return null;
	}


	public List<String> getAllInitialEvents(Document doc) {
		// TODO Auto-generated method stub
		return null;
	}

	

}
