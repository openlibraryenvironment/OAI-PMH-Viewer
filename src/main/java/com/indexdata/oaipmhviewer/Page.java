/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.indexdata.oaipmhviewer;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author ne
 */
public class Page {
  private static String LS = System.lineSeparator();


  /**
   * Builds the application page with the form
   * @param inputOaiUrl the initial user provided URL
   * @param finalOaiUrl the actually executed URL
   * @param listSets available sets from the OAI-PMH service
   * @param selectedSet the currently selected set
   * @param listMetadataFormats available metadata formats from the OAI-PMH service
   * @param selectedFormat the currently selected format
   * @param displayResponse the response on finalRequestUrl
   * @param gotOai flags if OAI-PMH data was retrieved
   * @return HTML page to send to the client
   */
  public static String getHtml(
              String inputOaiUrl,
              String finalOaiUrl,
              String listSets,
              String selectedSet,
              String listMetadataFormats,
              String selectedFormat,
              String displayResponse,
              boolean gotOai) {

    StringBuilder page = new StringBuilder("");

    page.append("<head><title>OAI-PMH viewer</title></head>").append(LS)
        .append("<body>").append(LS)
        .append(" <H1>Check an OAI-PMH service</H1>").append(LS)
        .append(" <form id=\"request\" method=\"post\" >").append(LS)
        .append("  <label for=\"url\"><h3>Enter an OAI-PMH URL</h3>")
        .append("<input type=\"text\" size=\"120\" id=\"oaiurl\" ")
        .append("     name=\"oaiurl\" value=\"")
        .append((inputOaiUrl != null ? inputOaiUrl : "")).append("\">&nbsp;")
        .append("  <input type=\"submit\" name=\"action\" value=\"Request\"> ")
        .append("<input type=\"submit\" name=\"action\" value=\"Clear\"><br>").append(LS);

    if (!gotOai) {
        page.append("<table border=\"0\" cellpadding=\"10\" width=\"80%\">")
            .append("<tr><td>The URL can be a base OAI-PMH URL or a complete OAI-PMH query.</td></tr>")
            .append(LS)
            .append("<tr><td>A base OAI-PMH URL could look like this: </td></tr>")
            .append(LS)
            .append("<tr><td>&nbsp;&nbsp;<b>http://my.oaiserver.com/view/oai/MY_INST_CODE/request</b></td></tr>")
            .append(LS)
            .append("<tr><td>A complete query for that same base URL could be: </td></tr>")
            .append("<tr><td>&nbsp;&nbsp;<b>http://my.oaiserver.com/view/oai/MY_INST_CODE/request?verb=ListRecords&set=myset&metadataPrefix=marc21</b></td></tr>")
            .append(LS)
            .append("<tr><td>In either case, once this service has recognized a genuine ")
            .append("base OAI-PMH URL, it will offer options for runnning various, ")
            .append("simple OAI-PMH requests against that remote service address.")
            .append("</td></tr>").append(LS)
            .append("<tr><td>At any time, an arbitrary OAI-PMH request can be made by ")
            .append("entering the request URL in the input field above and hitting ")
            .append(" Return or clicking 'Request'</td></tr>").append(LS)
            .append("</table>").append(LS);

    }

    if (finalOaiUrl.length() > 0 && gotOai) {
      page.append("<br>").append(LS)
          .append("<h3>Other requests</h3>").append(LS)
          .append("<input type=\"submit\" name=\"verb\" value=\"Identify\"> ")
          .append("<input type=\"submit\" name=\"verb\" value=\"ListSets\"> ")
          .append("<input type=\"submit\" name=\"verb\" value=\"ListMetadataFormats\"> ")
          .append("<br><br>").append(LS)
          .append(setsSelectList(listSets, selectedSet)).append("&nbsp;")
          .append(metadataPrefixSelectList(listMetadataFormats, selectedFormat)).append("&nbsp;")
          .append("<input type=\"submit\" name=\"verb\" value=\"ListRecords\">").append(LS)
          .append("<br><br>").append(LS);
    }
    if (finalOaiUrl.length() >0 ) {
      page.append("<h3>Latest request sent</h3>").append(finalOaiUrl)
          .append("<br><br>").append(LS)
          .append("<h3>Latest response received</h3>").append(LS)
          .append("<textarea rows=\"40\" cols=\"140\" name=\"results\" >")
          .append(displayResponse).append("</textarea>").append(LS);
    }

    page.append( " </form>").append(LS).append("</body>");
    return page.toString();
  }


  /**
   * Builds minimal page - ie before any URL is entered or in error scenarios
   * @param inputOaiUrl the initial user provided URL, if any
   * @param finalOaiUrl the actually executed URL, if any
   * @param message the response to the executed URL
   * @param gotOai flags if a valid OAI-PMH response was received
   * @return HTML page to send to the client
   */
  public static String getHtml(
              String inputOaiUrl,
              String finalOaiUrl,
              String message,
              boolean gotOai) {
    return getHtml(inputOaiUrl, finalOaiUrl, "", "", "", "", message, gotOai);
  }

    /**
   * Creates HTML select tag from OAI-PMH ListSets request
   * @param metadataFormats OAI-PMH XML containing available sets
   * @param selected defines the currently selected set
   * @return HTML select list
   */
  private static String setsSelectList (String sets, String selected) {
    StringBuilder selectList = new StringBuilder("");
    try {
      Element node =  DocumentBuilderFactory
        .newInstance()
        .newDocumentBuilder()
        .parse(new ByteArrayInputStream(sets.getBytes()))
        .getDocumentElement();
      NodeList setElements = node.getElementsByTagName("setSpec");
      selectList.append("<select id=\"set\" name=\"set\">" +LS);
      selectList.append("<option value=\"\">Select set</option>");
      for (int i=0; i<setElements.getLength(); i++) {
        String value = setElements.item(i).getTextContent();
        selectList.append("<option value=\"").append(value).append("\"")
                  .append(value.equals(selected) ? " selected " : "")
                  .append(">");
        selectList.append(value).append("</option>").append(LS);
      }
      selectList.append("</select>").append(LS);
      return selectList.toString();
    } catch (IOException | ParserConfigurationException | SAXException e) {
      System.out.println("Error creating DOM for ListSets XML: " + e.getMessage());
      return "<select id=\"set\"></select>";
    }
  }

  /**
   * Creates HTML select tag from OAI-PMH ListMetadataFormats request
   * @param metadataFormats OAI-PMH XML containing available meta-data formats
   * @param selected defines the currently selected format
   * @return HTML select list
   */
  private static String metadataPrefixSelectList (String metadataFormats, String selected) {
    StringBuilder selectList = new StringBuilder("");
    try {
      Element node =  DocumentBuilderFactory
        .newInstance()
        .newDocumentBuilder()
        .parse(new ByteArrayInputStream(metadataFormats.getBytes()))
        .getDocumentElement();
      NodeList setElements = node.getElementsByTagName("metadataPrefix");
      selectList.append("<select id=\"metadataPrefix\" name=\"metadataPrefix\">" +LS);
      selectList.append("<option value=\"\">Select metadataPrefix</option>");
      for (int i=0; i<setElements.getLength(); i++) {
        String value = setElements.item(i).getTextContent();
        selectList.append("<option value=\"").append(value).append("\"")
                  .append(value.equals(selected) ? " selected " : "")
                  .append(">");
        selectList.append(value).append("</option>").append(LS);
      }
      selectList.append("</select>").append(LS);
      return selectList.toString();
    } catch (IOException | ParserConfigurationException | SAXException e) {
      System.out.println("Error creating DOM for ListMetadataFormats XML: " + e.getMessage());
      return "<select id=\"metadataPrefix\"></select>";
    }

  }

}
