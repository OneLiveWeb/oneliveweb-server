package org.entermediadb.asset.util;

import java.util.HashMap;
import java.util.Map;

import org.openedit.BaseTestCase;
import org.openedit.data.SearcherManager;
import org.openedit.util.Replacer;

public class ReplacerTest extends BaseTestCase
{
	public void testReplacer() throws Exception
	{
		SearcherManager manager = (SearcherManager)getBean("searcherManager");
		Replacer replacer = new Replacer();
		
		replacer.setCatalogId("system");
		replacer.setAlwaysReplace(true);
		replacer.setSearcherManager(manager);
		String code = "${one}/${longvariable}/${user.email}/${notfound}/${two}/${user.id}${nogap1}${nogap2}";
		Map vars = new HashMap();
		vars.put("user", "admin");
		vars.put("one", "Uno");
		vars.put("nogap1", "123");
		vars.put("nogap2", "456");
		vars.put("two", "TOOOOLONG");
		vars.put("longvariable", "short");
		
		String returned = replacer.replace(code, vars);
		assertEquals("Uno/short/support@openedit.org//TOOOOLONG/admin123456",returned);
		
		
	}
}
