package com.marekhudyma.htmlparserperformance;


import java.util.List;

public interface HtmlParser {

    public List<CharSequence> parse(CharSequence html);

}
