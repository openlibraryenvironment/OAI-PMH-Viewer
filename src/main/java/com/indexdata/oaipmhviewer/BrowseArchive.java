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
import org.xml.sax.SAXException;

import io.vertx.core.AbstractVerticle;
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

  @Override
  public void start(Promise<Void> promise) {
    HttpServer server = vertx.createHttpServer();
    WebClient client = WebClient.create(vertx);
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    router.route().handler(routingContext -> {
      HttpServerRequest req = routingContext.request();
      HttpServerResponse resp = routingContext.response();

      String inputOaiUrl = req.getFormAttribute("oaiurl");

      if (inputOaiUrl != null) {
        String httpUrl = setProtocolToHttp(inputOaiUrl);
        URL pURL;
        try {
          pURL = new URL(httpUrl);

          // If specific verb requested, use that, otherwiser use provided query as is (if any)
          String verb = req.getFormAttribute("verb");
          String query =
            Arrays.asList("Identify",
                          "ListSets",
                          "ListMetadataFormats").contains(verb) ?
            "verb="+verb :
            (pURL.getQuery() != null ? pURL.getQuery() : "");

          final String finalOaiUrl =
                  pURL.getProtocol() + "://"
                  + pURL.getAuthority()
                  + pURL.getPath()
                  + (query.isEmpty() ? "" : "?" + query);

          // Attempt OAI-PMH request
          client.get(pURL.getAuthority(), pURL.getPath() + (query.isEmpty() ? "" : "?" + query))
            .send(ar -> {
              if (ar.succeeded()) {
                HttpResponse<Buffer> oaiResponse = ar.result();
                if (ar.result().statusCode() == 200) {
                  String responseDisplay = prettyPrintOaiResponseOrDump(oaiResponse.body().toString());
                  String page = buildPage(inputOaiUrl, finalOaiUrl, responseDisplay);
                  resp.end(page);
                } else {
                  String error =
                    "Error " + ar.result().statusCode()
                    + " " + ar.result().statusMessage()
                    + LS + LS
                    + firstCharactersOf(oaiResponse.bodyAsString(),2000);
                  String page = buildPage(inputOaiUrl, finalOaiUrl, error);
                  resp.end(page);
                }
              } else {
                String error = ar.cause().getMessage();
                String page = buildPage(inputOaiUrl, finalOaiUrl, error);
                resp.end(page);
              }
            });
        } catch (MalformedURLException mue) {
          String page = buildPage(inputOaiUrl,
                                  "Couldn't create valid OAI request",
                                  mue.getMessage());
          resp.end(page);
        } catch (Exception e) {
          String page = buildPage(inputOaiUrl,
                                  "Couldn't create valid OAI request",
                                  e.getMessage());
          resp.end(page);
        }
      } else {
        // No OAI URL provided yet, return empty form
        resp.end(buildPage("", "", ""));
      }
    });

    server.requestHandler(router).listen(8088, result -> {
      if (result.succeeded()) {
        promise.complete();
      } else {
        promise.fail(result.cause());
      }
    });
  }

  private String buildPage (String pOaiUrl, String requestUri, String response) {

    return "<head>"
      + " <title>OAI-PMH browser</title>"
      + "</head>"
      + "<body>"
      + " <H1>Test an OAI-PMH service</H1>"
      + " <form id=\"request\" method=\"post\" >"
      + "  <label for=\"url\">OAI-PMH URL</label><br>"
      + "  <input type=\"text\" size=\"140\" id=\"oaiurl\" "
      + "      name=\"oaiurl\" value=\""
      +           (pOaiUrl != null ? pOaiUrl : "") + "\"><br><br>"
      + "  <input type=\"submit\" name=\"verb\" value=\"Identify\"> "
      + "  <input type=\"submit\" name=\"verb\" value=\"ListSets\"> "
      + "  <input type=\"submit\" name=\"verb\" value=\"ListMetadataFormats\"> "
      + "  <input type=\"submit\" name=\"verb\" value=\"ListRecords\"><br><br>"
      +   (requestUri.length() > 0 ?
          "  <label>Request URI:</label>  <b>" + requestUri
          + "</b><br><br>"
          + "<textarea rows=\"40\" cols=\"140\" name=\"results\" >"
          + response
          + "</textarea>"
          :
          "")
      + " </form>"
      + "</body>";
  }

  private String prettyPrintOaiResponseOrDump (String resp) {
    String prettyOaiXmlOrDump;
    if (resp != null &&
         (resp.contains("<OAI-PMH") || resp.contains("<error_code>")))
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
        prettyOaiXmlOrDump = result.getWriter().toString();
      } catch (IOException | IllegalArgumentException | ParserConfigurationException | TransformerException | SAXException e) {
        prettyOaiXmlOrDump = "Could not parse/transform response: "
                + e.getMessage() + LS + resp;
      }
    } else {
      prettyOaiXmlOrDump =
        "Did not receieve a proper OAI-PMH response for the given URL: "
        + LS + LS
        + (resp != null ? firstCharactersOf(resp, 2000) : " No response created.");
    }
    return prettyOaiXmlOrDump;
  }

  private String setProtocolToHttp (String url) {
    return (
      url != null && !url.isEmpty() && !url.matches("https?://.*")) ?
      "http://" + url : url;
  }

  private String firstCharactersOf(String str, int chars) {
    if (str != null) {
      return str.substring(0, Math.min(str.length()-1,chars));
    } else {
      return str;
    }
  }
}
