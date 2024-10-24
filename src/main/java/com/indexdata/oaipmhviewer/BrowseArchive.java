package com.indexdata.oaipmhviewer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import io.vertx.ext.web.client.WebClientOptions;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
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
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.FaviconHandler;
import io.vertx.ext.web.handler.StaticHandler;

/**
 *
 * @author ne
 */
public class BrowseArchive extends AbstractVerticle {
  static String LS = System.lineSeparator();

  @Override
  public void start(Promise<Void> promise) {
    HttpServer server = vertx.createHttpServer();
    // Port that this server will listen on
    final int port = getPort();

    Router router = Router.router(vertx);
    // Handle POSTed FORM data
    router.route().handler(BodyHandler.create());

    router.route().handler(FaviconHandler.create());
    router.route("/static/*").handler(StaticHandler.create("static"));

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
          // Allow user to enter URI without protocol, but assume http then.
          pURL = (inputOaiUrl.matches("https?://.*") ?
                  new URL(inputOaiUrl) : new URL("http://"+inputOaiUrl));

          String query;
          if (Arrays.asList("Identify",
                            "ListSets",
                            "ListMetadataFormats").contains(verb)) {
            query = "verb="+verb;
          } else if ("ListRecords".equals(verb)) {
            query = "verb="+verb+"&set="+set+"&metadataPrefix="+metadataPrefix;
          } else if (pURL.getQuery() != null) {
            query = pURL.getQuery();
          } else {
            query = "";
          }

          final String finalOaiUrl =
                  pURL.getProtocol() + "://"
                  + pURL.getAuthority()
                  + pURL.getPath()
                  + (query.isEmpty() ? "" : "?" + query);

          // Attempt OAI-PMH request
          prepareRequest(pURL, query).send(ar -> {
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
                      String page = Page.getHtml(inputOaiUrl,
                                                 finalOaiUrl,
                                                 listSets,
                                                 set,
                                                 listMetadataFormats,
                                                 metadataPrefix,
                                                 displayOaiResponse,
                                                 true);
                      resp.end(page);
                    });
                  } catch (NotAnOaiPmhResponseException nopre) {
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
                              + first2000CharactersOf(oaiResponse.bodyAsString()));
                }
              } else {
                sendErrorResponse(
                        resp,
                        inputOaiUrl,
                        finalOaiUrl,
                        ar.cause().getMessage());
              }
            });
        } catch (Exception e) {
          sendErrorResponse(
                  resp,
                  inputOaiUrl,
                  "Couldn't create valid OAI request",
                  e.getMessage());
        }
      } else {
        // No OAI URL provided yet, return empty form
        resp.end(Page.getHtml("", "", "", false));
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
    resp.end(Page.getHtml(inputOaiUrl, finalOaiUrl, message, false));
  }

  /**
   * Makes OAI-PMH request to remote service for available sets
   * @param url base URL of the remote OAI-PMH service
   * @return a future from which to getHtml the servers response
   */
  private Future<String> listSets(URL url) {
    Promise<String> promise = Promise.promise();
    prepareRequest(url, "verb=ListSets").send(ar -> {
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
   * @return a future from which to getHtml the servers response
   */
  private Future<String> listMetadataFormats(URL url) {
    Promise<String> promise = Promise.promise();
    prepareRequest(url,"verb=ListMetadataFormats").send(ar -> {
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
   * Will parse provided string as OAI-PMH XML, remove existing white space and re-indent
   * @param resp a String containing the OAI-PMH XML to format
   * @return Indented OAI-PMH XML for display
   * @throws NotAnOaiPmhResponseException if 'resp' is not recognized as OAI-PMH XML
   */
  private String prettyPrintOaiResponse (String resp) throws NotAnOaiPmhResponseException {
    String prettyOaiXml;
    if (resp != null && resp.contains("<OAI-PMH"))
    {
      try {

        // StreamResult to hold the transformed document
        StreamResult result = new StreamResult(new StringWriter());

        // Build DOM document
        Document doc = DocumentBuilderFactory
          .newInstance()
          .newDocumentBuilder()
          .parse(new ByteArrayInputStream(resp.getBytes()));

        // First remove existing whitespace between elements
        // (or it's preserved, messing up the indentation)
        XPathFactory xfact = XPathFactory.newInstance();
        XPath xpath = xfact.newXPath();
        NodeList empty =
          (NodeList)xpath.evaluate("//text()[normalize-space(.) = '']",
                             doc, XPathConstants.NODESET);
        for (int i = 0; i < empty.getLength(); i++) {
          Node node = empty.item(i);
          node.getParentNode().removeChild(node);
        }

        // Then transform the XML with indentation
        DOMSource source = new DOMSource(doc.getDocumentElement());
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(source, result);

        // Return result of transformation as string
        prettyOaiXml = result.getWriter().toString();

      } catch (IOException | IllegalArgumentException | ParserConfigurationException | TransformerException | SAXException | TransformerFactoryConfigurationError | XPathExpressionException e) {
        String message = "Could not parse/transform response: " + e.getMessage() + LS + LS + resp;
        throw new NotAnOaiPmhResponseException(message);
      }
    } else {
      String message = resp != null ? first2000CharactersOf(resp) : " No response created.";
      throw new NotAnOaiPmhResponseException(message);
    }
    return prettyOaiXml;
  }

  /**
   * Return a chunk of the input 'str' for partial dump of it
   * @param str input string to cut off
   * @return chars characters of 'str'
   */
  private String first2000CharactersOf(String str) {
    if (str != null) {
      return str.substring(0, Math.min(str.length(),2000));
    } else {
      return null;
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

  private HttpRequest<Buffer> prepareRequest (URL pURL, String query) {
    String qry = (query.isEmpty() ? "" : "?" + query);
    if (pURL.getProtocol().equals("https")) {
      WebClient client = WebClient.create(vertx,
              new WebClientOptions().setSsl(true));
      return client.get(443, pURL.getAuthority(), pURL.getPath() + qry);
    } else {
      WebClient client = WebClient.create(vertx);
      return client.get(pURL.getAuthority(), pURL.getPath() + qry);
    }
  }
}
