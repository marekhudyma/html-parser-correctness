package com.marekhudyma.htmlparserperformance;


import jodd.lagarto.*;

import java.util.LinkedList;
import java.util.List;

public interface HtmlParser {

    public List<CharSequence> parse(CharSequence html);

}
