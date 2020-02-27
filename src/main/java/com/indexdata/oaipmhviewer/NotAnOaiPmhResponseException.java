/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.indexdata.oaipmhviewer;

/**
 *
 * @author ne
 */
public class NotAnOaiPmhResponseException extends Exception {
  String message = "";
  
  NotAnOaiPmhResponseException(String message) {
    this.message = message;
  }
  
  @Override
  public String getMessage() {
    return message;
  } 
}
