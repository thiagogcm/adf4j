/** adf4j — Atlassian Document Format processing for Java. Only the API packages below are exported. */
module dev.nthings.adf4j {
  requires org.slf4j;
  requires tools.jackson.core;
  requires tools.jackson.databind;
  requires org.commonmark;
  requires org.commonmark.ext.gfm.alerts;
  requires org.commonmark.ext.gfm.strikethrough;
  requires org.commonmark.ext.gfm.tables;
  requires org.commonmark.ext.heading.anchor;
  requires org.commonmark.ext.image.attributes;
  requires org.commonmark.ext.task.list.items;
  requires org.jsoup;

  exports dev.nthings.adf4j;
  exports dev.nthings.adf4j.ast;
  exports dev.nthings.adf4j.confluence;
}
