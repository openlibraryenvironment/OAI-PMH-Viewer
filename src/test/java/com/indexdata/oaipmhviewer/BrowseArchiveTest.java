/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.indexdata.oaipmhviewer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

/**
 *
 * @author ne
 */
@RunWith(VertxUnitRunner.class)
public class BrowseArchiveTest {
  private Vertx vertx;
  
  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    vertx.deployVerticle(BrowseArchive.class.getName(), context.asyncAssertSuccess());
  }
  
  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());    
  }
  
  @Test
  public void testMyApplication(TestContext context) {
    final Async async = context.async();
    WebClient client = WebClient.create(vertx);
    
    client.get(8088, "localhost", "/")
            .send(ar -> {
              if (ar.succeeded()) {
                HttpResponse<Buffer> response = ar.result();
                String verify = "OAI-PMH";
                context.assertTrue(response.bodyAsString().contains(verify), "Page did not contain '" + verify + "'");  
                async.complete();
              } else {
                context.fail(ar.cause().getMessage());
                async.complete();
              }
            });
  }
  
}
