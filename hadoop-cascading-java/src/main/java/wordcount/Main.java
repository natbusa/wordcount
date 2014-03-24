/*
 * Copyright (c) 2007-2013 Concurrent, Inc. All Rights Reserved.
 *
 * Project and contact information: http://www.cascading.org/
 *
 * This file is part of the Cascading project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package wordcount;

import java.util.Properties;

import cascading.flow.Flow;
import cascading.flow.FlowDef;
import cascading.flow.hadoop.HadoopFlowConnector;
import cascading.operation.aggregator.Count;
import cascading.operation.regex.RegexFilter;
import cascading.operation.regex.RegexSplitGenerator;
import cascading.pipe.Each;
import cascading.pipe.Every;
import cascading.pipe.GroupBy;
import cascading.pipe.Pipe;
import cascading.property.AppProps;
import cascading.scheme.Scheme;
import cascading.scheme.hadoop.TextLine;
import cascading.tap.Tap;
import cascading.tap.hadoop.Hfs;
import cascading.tuple.Fields;

import cascading.flow.FlowProcess;
import cascading.operation.BaseOperation;
import cascading.operation.Function;
import cascading.operation.FunctionCall;
import cascading.tuple.Fields;
import cascading.tuple.Tuple;
import cascading.tuple.TupleEntry;

class ScrubFunction extends BaseOperation implements Function
  {
  public ScrubFunction( Fields fieldDeclaration )
    {
    super( 1, fieldDeclaration );
    }

  public void operate( FlowProcess flowProcess, FunctionCall functionCall )
    {
    TupleEntry argument = functionCall.getArguments();
    String token = scrubText( argument.getString( 0 ) );

    if( token.length() > 0 )
      {
      Tuple result = new Tuple();
      result.add( token );
      functionCall.getOutputCollector().add( result );
      }
    }

  public String scrubText( String text )
    {
    return text.trim().toLowerCase();
    }
  }

public class
  Main
  {
  public static void
  main( String[] args )
    {
    String docPath = args[ 0 ];
    String wcPath = args[ 1 ];

    Properties properties = new Properties();
    AppProps.setApplicationJarClass( properties, Main.class );
    HadoopFlowConnector flowConnector = new HadoopFlowConnector( properties );

    // create source and sink taps
    Tap docTap = new Hfs( new TextLine(), docPath );
    Tap wcTap = new Hfs( new TextLine(), wcPath );

    // specify a regex operation to split the "document" text lines into a token stream
    Fields token = new Fields( "token" );
    RegexSplitGenerator splitter = new RegexSplitGenerator( token, "\\W+" );
    
    // only returns "words"
    Fields text = new Fields( "line" );
    Pipe wordPipe = new Each("WP", text, splitter, Fields.RESULTS );

    // replace tokens with lowercase words
    Fields word = new Fields( "word" );
    wordPipe = new Each( wordPipe, token, new ScrubFunction( word ), Fields.RESULTS );

    // determine the word counts
    Pipe wcPipe = new Pipe( "wc", wordPipe );
    wcPipe = new GroupBy( wcPipe, word );
    wcPipe = new Every( wcPipe, Fields.ALL, new Count(), Fields.ALL );

    // connect the taps, pipes, etc., into a flow
    FlowDef flowDef = FlowDef.flowDef()
     .setName( "wc" )
     .addSource( wordPipe, docTap )
     .addTailSink( wcPipe, wcTap );

    // run the flow
    Flow wcFlow = flowConnector.connect( flowDef );
    wcFlow.writeDOT( "dot/wc.dot" );    
    wcFlow.complete();
    }
  }
