package com.marekhudyma.htmlparserperformance;

import jodd.lagarto.*;

import java.util.LinkedList;
import java.util.List;

public class LagartoHtmlParser implements HtmlParser {

    @Override
    public List<CharSequence> parse(CharSequence html) {

        LagartoParserConfig config = new LagartoParserConfig()
                .setEnableConditionalComments(false)
                .setEnableRawTextModes(false);
        LagartoParser lagartoParser = new LagartoParser(config, html);
        TagVisitorImpl tagVisitor = new TagVisitorImpl();
        lagartoParser.parse(tagVisitor);
        return tagVisitor.getLinks();
    }

    private static class TagVisitorImpl implements TagVisitor {

        private static final String HREF = "href";
        private List<CharSequence> links = new LinkedList<>();
        private CharSequence href = null;

        public List<CharSequence> getLinks() {
            return links;
        }

        @Override
        public void tag(Tag tag) {
            href = tag.getAttributeValue(HREF);
            if (href != null) {
                links.add(href);
            }
        }

        @Override
        public void start() {

        }

        @Override
        public void end() {

        }

        @Override
        public void doctype(Doctype doctype) {

        }

        @Override
        public void script(Tag tag, CharSequence body) {

        }

        @Override
        public void comment(CharSequence comment) {

        }

        @Override
        public void text(CharSequence text) {

        }

        @Override
        public void condComment(CharSequence expression, boolean isStartingTag, boolean isHidden, boolean isHiddenEndTag) {

        }

        @Override
        public void xml(CharSequence version, CharSequence encoding, CharSequence standalone) {

        }

        @Override
        public void cdata(CharSequence cdata) {

        }

        @Override
        public void error(String message) {

        }
    }
}
