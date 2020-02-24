package com.indexdata.oaipmhviewer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;

/**
 *
 * @author ne
 */
public class BrowseArchive extends AbstractVerticle {
  static String LS = System.lineSeparator();
  WebClient client;

  @Override
  public void start(Promise<Void> promise) {
    HttpServer server = vertx.createHttpServer();
    // Port that this server will listen on
    final int port = getPort();

    Router router = Router.router(vertx);
    // Handle POSTed FORM data
    router.route().handler(BodyHandler.create());

    client = WebClient.create(vertx);

    router.route().handler(routingContext -> {
      HttpServerRequest req = routingContext.request();
      HttpServerResponse resp = routingContext.response();

      final String inputOaiUrl = req.getFormAttribute("oaiurl");
      final String action = req.getFormAttribute("action");
      final String verb = req.getFormAttribute("verb");
      final String set = req.getFormAttribute("set");
      final String metadataPrefix = req.getFormAttribute("metadataPrefix");

      if (inputOaiUrl != null && !inputOaiUrl.isEmpty() && !"Clear".equals(action)) {
        URL pURL;
        try {
          // Allow user to enter URI without protocol. Always use http for now, anyway.
          pURL = (inputOaiUrl.matches("https?://.*") ?
                  new URL(inputOaiUrl) : new URL("http://"+inputOaiUrl));

          String query = "";
          if (Arrays.asList("Identify",
                            "ListSets",
                            "ListMetadataFormats").contains(verb)) {
            query = "verb="+verb;
          } else if ("ListRecords".equals(verb)) {
            query = "verb="+verb+"&set="+set+"&metadataPrefix="+metadataPrefix;
          } else if (pURL.getQuery() != null) {
            query = pURL.getQuery();
          } else {
            query = "verb=Identify";
          }

          final String finalOaiUrl =
                  "http://"
                  + pURL.getAuthority()
                  + pURL.getPath()
                  + (query.isEmpty() ? "" : "?" + query);

          // Attempt OAI-PMH request
          client.get(pURL.getAuthority(), pURL.getPath() + (query.isEmpty() ? "" : "?" + query))
            .send(ar -> {
              if (ar.succeeded()) {
                HttpResponse<Buffer> oaiResponse = ar.result();
                if (ar.result().statusCode() == 200) {
                  try {
                    String displayOaiResponse = prettyPrintOaiResponse(oaiResponse.body().toString());
                    Future<String> promisedSets = listSets(pURL);
                    Future<String> promisedFormats = listMetadataFormats(pURL);
                    CompositeFuture.all(promisedSets, promisedFormats).onComplete( cfar -> {
                      String listSets = cfar.result().resultAt(0).toString();
                      String listMetadataFormats = cfar.result().resultAt(1).toString();
                      String page = buildPage(inputOaiUrl, finalOaiUrl, listSets, set, listMetadataFormats, metadataPrefix, displayOaiResponse, true);
                      resp.end(page);
                    });
                  } catch (NotOaiPmhResponseException nopre) {
                    sendErrorResponse(
                            resp,
                            inputOaiUrl,
                            finalOaiUrl,
                            "Did not receieve a proper OAI-PMH response for the given URL: "
                                + LS + LS + nopre.getMessage());
                  }
                } else {
                  sendErrorResponse(
                          resp,
                          inputOaiUrl,
                          finalOaiUrl,
                          "Error " + ar.result().statusCode()
                              + " " + ar.result().statusMessage()
                              + LS + LS
                              + firstCharactersOf(oaiResponse.bodyAsString(),2000));
                }
              } else {
                sendErrorResponse(
                        resp,
                        inputOaiUrl,
                        finalOaiUrl,
                        ar.cause().getMessage());
              }
            });
        } catch (MalformedURLException mue) {
          sendErrorResponse(
                  resp,
                  inputOaiUrl,
                  "Couldn't create valid OAI request",
                  mue.getMessage());

        } catch (Exception e) {
          sendErrorResponse(
                  resp,
                  inputOaiUrl,
                  "Couldn't create valid OAI request",
                  e.getMessage());
        }
      } else {
        // No OAI URL provided yet, return empty form
        resp.end(buildPage("", "", "", false));
      }
    });

    server.requestHandler(router).listen(port, result -> {
      if (result.succeeded()) {
        System.out.println("OAI-PMH-Viever listening on port [" + port + "]");
        promise.complete();
      } else {
        promise.fail(result.cause());
      }
    });

  }

  /**
   * Convenience method for building the page in error scenarios
   * @param resp the server response to send
   * @param inputOaiUrl the initial user provided URL, if any
   * @param finalOaiUrl the actually executed URL, if any
   * @param message the error message to return
   */
  private void sendErrorResponse(HttpServerResponse resp, String inputOaiUrl, String finalOaiUrl, String message) {
    resp.end(buildPage(inputOaiUrl, finalOaiUrl, message, false));
  }

  /**
   * Makes OAI-PMH request to remote service for available sets
   * @param url base URL of the remote OAI-PMH service
   * @return a future from which to get the servers response
   */
  private Future<String> listSets(URL url) {
    Promise<String> promise = Promise.promise();
    client.get(url.getAuthority(), url.getPath() + "?verb=ListSets").send(ar -> {
      String resp;
      if (ar.succeeded()) {
        HttpResponse<Buffer> oaiResponse = ar.result();
        if (oaiResponse != null) {
          resp = oaiResponse.bodyAsString();
        } else {
          resp = "Response was null";
        }
        promise.complete(resp);
      } else if (ar.failed()) {
        String fail = ar.cause().getMessage();
        promise.complete("getSets failed for url " + url + " [" + fail + "]");
      }
    });
    return promise.future();
  }

  /**
   * Makes OAI-PMH request to remote service for available metadata prefixes
   * @param url base URL of the remote OAI-PMH service
   * @return a future from which to get the servers response
   */
  private Future<String> listMetadataFormats(URL url) {
    Promise<String> promise = Promise.promise();
    client.get(url.getAuthority(), url.getPath() + "?verb=ListMetadataFormats").send(ar -> {
      String resp;
      if (ar.succeeded()) {
        HttpResponse<Buffer> oaiResponse = ar.result();
        if (oaiResponse != null) {
          resp = oaiResponse.bodyAsString();
        } else {
          resp = "Response was null";
        }
        promise.complete(resp);
      } else if (ar.failed()) {
        String fail = ar.cause().getMessage();
        promise.complete("getFormats failed for url " + url + " [" + fail + "]");
      }
    });
    return promise.future();
  }

  /**
   * Builds minimal page - ie before any URL is entered or in error scenarios
   * @param inputOaiUrl the initial user provided URL, if any
   * @param finalRequestUrl the actually executed URL, if any
   * @param response the response to the executed URL
   * @return HTML page to send to the client
   */
  private String buildPage (String inputOaiUrl, String finalRequestUrl, String response, boolean hasOai) {
    return buildPage(inputOaiUrl, finalRequestUrl, "", "", "", "", response, hasOai);
  }

  /**
   * Builds the page with the form
   * @param inputOaiUrl the initial user provided URL
   * @param finalRequestUrl the actually executed URL
   * @param sets available sets from the OAI-PMH service
   * @param selectedSet the currently selected set
   * @param formats available metadata formats from the OAI-PMH service
   * @param selectedFormat the currently selected format
   * @param oaiResponse the response on finalRequestUrl
   * @return HTML page to send to the client
   */
  private String buildPage (String inputOaiUrl, String finalRequestUrl,
          String sets, String selectedSet, String formats, String selectedFormat,
          String oaiResponse,
          boolean hasOai) {

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
        .append("<input type=\"submit\" name=\"action\" value=\"Clear\"><br>").append(LS)
        .append("<i>Can be a base OAI-PMH URL as well as a complete OAI-PMH query</i><br>");

    if (finalRequestUrl.length() > 0 && hasOai) {
      page.append("<br><br>").append(LS)
          .append("<h3>Modify the request</h3>").append(LS)
          .append("<input type=\"submit\" name=\"verb\" value=\"Identify\"> ")
          .append("<input type=\"submit\" name=\"verb\" value=\"ListSets\"> ")
          .append("<input type=\"submit\" name=\"verb\" value=\"ListMetadataFormats\"> ")
          .append("<br><br>").append(LS)
          .append(setsSelectList(sets, selectedSet)).append("&nbsp;")
          .append(metadataPrefixSelectList(formats, selectedFormat)).append("&nbsp;")
          .append("<input type=\"submit\" name=\"verb\" value=\"ListRecords\">").append(LS)
          .append("<br><br>").append(LS);
    }
    if (finalRequestUrl.length() >0 ) {
      page.append("<label><h3>Latest request sent</h3></label>").append(finalRequestUrl)
          .append("</b><br><br>").append(LS)
          .append("<h3>Latest response received</h3>").append(LS)
          .append("<textarea rows=\"40\" cols=\"140\" name=\"results\" >")
          .append(oaiResponse).append("</textarea>").append(LS);
    }

    page.append( " </form>").append(LS).append("</body>");

    return page.toString();
  }

  /**
   * Creates HTML select tag from OAI-PMH ListSets request
   * @param metadataFormats OAI-PMH XML containing available sets
   * @param selected defines the currently selected set
   * @return HTML select list
   */
  private String setsSelectList (String sets, String selected) {
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
  private String metadataPrefixSelectList (String metadataFormats, String selected) {
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

  /**
   * Will parse provided string as OAI-PMH XML
   * @param resp a String containing the OAI-PMH XML to format
   * @return Indented OAI-PMH XML for display
   * @throws NotOaiPmhResponseException if resp is not recognized as OAI-PMH XML
   */
  private String prettyPrintOaiResponse (String resp) throws NotOaiPmhResponseException {
    String prettyOaiXml;
    if (resp != null && resp.contains("<OAI-PMH"))
    {
      try {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        //initialize StreamResult with File object to save to file
        StreamResult result = new StreamResult(new StringWriter());
        Element node =  DocumentBuilderFactory
          .newInstance()
          .newDocumentBuilder()
          .parse(new ByteArrayInputStream(resp.getBytes()))
          .getDocumentElement();
        DOMSource source = new DOMSource(node);
        transformer.transform(source, result);
        prettyOaiXml = result.getWriter().toString();
      } catch (IOException | IllegalArgumentException | ParserConfigurationException | TransformerException | SAXException e) {
        String message = "Could not parse/transform response: " + e.getMessage() + LS + LS + resp;
        throw new NotOaiPmhResponseException(message);
      }
    } else {
      String message = resp != null ? firstCharactersOf(resp, 2000) : " No response created.";
      throw new NotOaiPmhResponseException(message);
    }
    return prettyOaiXml;
  }

  /**
   * Return a chunk of the input str for partial dump of it
   * @param str input string to cut off
   * @param chars maximum length of the dump
   * @return chars characters of str
   */
  private String firstCharactersOf(String str, int chars) {
    if (str != null) {
      return str.substring(0, Math.min(str.length(),chars));
    } else {
      return str;
    }
  }

  /**
   * Reads service listener port from command line parameter
   * @return port number provided on command line or default 8088
   */
  private int getPort () {
    int port;
    try {
      port = Integer.parseInt(System.getProperty("http.port"));
    } catch (NumberFormatException | NullPointerException e) {
      port = 8088;
    }
    return port;
  }
}
