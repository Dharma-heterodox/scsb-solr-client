package org.recap.model.search.resolver.impl.Bib;

import org.recap.model.search.resolver.BibValueResolver;
import org.recap.model.solr.BibItem;

import java.util.Date;

/**
 * Created by rajeshbabuk on 27/10/16.
 */
public class BibLastUpdatedDateValueResolver implements BibValueResolver {
    @Override
    public Boolean isInterested(String field) {
        return field.equalsIgnoreCase("BibLastUpdatedDate");
    }

    @Override
    public void setValue(BibItem bibItem, Object value) {
        bibItem.setBibLastUpdatedDate((Date) value);
    }
}

