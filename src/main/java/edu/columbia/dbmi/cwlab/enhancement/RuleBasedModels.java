package edu.columbia.dbmi.cwlab.enhancement;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie;

public class RuleBasedModels implements Serializable{
	private static final long serialVersionUID = 901126911124L;
	AhoCorasickDoubleArrayTrie<String> acdat = new AhoCorasickDoubleArrayTrie<String>();
	Map<String,String> dir=new HashMap<String,String>();
	public AhoCorasickDoubleArrayTrie<String> getAcdat() {
		return acdat;
	}
	public void setAcdat(AhoCorasickDoubleArrayTrie<String> acdat) {
		this.acdat = acdat;
	}
	public Map<String, String> getDir() {
		return dir;
	}
	public void setDir(Map<String, String> dir) {
		this.dir = dir;
	}
	
	
}
