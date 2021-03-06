package org.recap.model.search.resolver.impl.Bib;

import org.recap.model.search.resolver.BibValueResolver;
import org.recap.model.solr.BibItem;

import java.util.List;

/**
 * Created by peris on 9/29/16.
 */
public class ISSNValueResolver implements BibValueResolver {
    @Override
    public Boolean isInterested(String field) {
        return field.equalsIgnoreCase("ISSN");
    }

    @Override
    public void setValue(BibItem bibItem, Object value) {
        bibItem.setIssn((List<String>)value);
    }

}
