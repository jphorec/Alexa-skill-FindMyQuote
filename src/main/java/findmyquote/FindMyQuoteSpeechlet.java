/**
    Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.

    Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at

        http://aws.amazon.com/apache2.0/

    or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package findmyquote;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.*;
import com.amazon.speech.ui.*;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
/**
 * This sample shows how to create a Lambda function for handling Alexa Skill requests that:
 * 
 * <ul>
 * <li><b>Web service</b>: communicate with an external web service to get events for specified days
 * in history (Wikipedia API)</li>
 * <li><b>Pagination</b>: after obtaining a list of events, read a small subset of events and wait
 * for user prompt to read the next subset of events by maintaining session state</li>
 * <p>
 * <li><b>Dialog and Session state</b>: Handles two models, both a one-shot ask and tell model, and
 * a multi-turn dialog model</li>
 * <li><b>SSML</b>: Using SSML tags to control how Alexa renders the text-to-speech</li>
 * </ul>
 * <p>
 * <h2>Examples</h2>
 * <p>
 * <b>One-shot model</b>
 * <p>
 * User: "Alexa, ask History Buff what happened on August thirtieth."
 * <p>
 * Alexa: "For August thirtieth, in 2003, [...] . Wanna go deeper in history?"
 * <p>
 * User: "No."
 * <p>
 * Alexa: "Good bye!"
 * <p>
 * 
 * <b>Dialog model</b>
 * <p>
 * User: "Alexa, open History Buff"
 * <p>
 * Alexa: "History Buff. What day do you want events for?"
 * <p>
 * User: "August thirtieth."
 * <p>
 * Alexa: "For August thirtieth, in 2003, [...] . Wanna go deeper in history?"
 * <p>
 * User: "Yes."
 * <p>
 * Alexa: "In 1995, Bosnian war [...] . Wanna go deeper in history?"
 * <p>
 * User: "No."
 * <p>
 * Alexa: "Good bye!"
 * <p>
 */
public class FindMyQuoteSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(FindMyQuoteSpeechlet.class);

    /**
     * URL prefix to download movie info from QuoDB.
     */
    private static final String URL_PREFIX =
            "http://api.quodb.com/search/";

    /**
     * Constant defining number of movies to be read at one time.
     */
    private static final int PAGINATION_SIZE = 1;

    /**
     * Constant defining session attribute key for the movie index.
     */
    private static final String SESSION_INDEX = "index";

    /**
     * Constant defining session attribute key for the moive text key.
     */
    private static final String SESSION_TEXT = "text";

    /**
     * Constant defining session attribute key for the intent slot key for the quote for finding the movie.
     */
    private static final String SLOT_PHRASE = "phrase";



    @Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        // any initialization logic goes here
    }

    @Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
            throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        return getWelcomeResponse();
    }

    @Override
    public SpeechletResponse onIntent(final IntentRequest request, final Session session)
            throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        Intent intent = request.getIntent();
        String intentName = intent.getName();

        if ("GetFirstMovieIntent".equals(intentName)) {
            return handleFirstEventRequest(intent, session);
        } else if ("GetNextMovieIntent".equals(intentName)) {
            return handleNextEventRequest(session);
        } else if ("AMAZON.HelpIntent".equals(intentName)) {
            // Create the plain text output.
            String speechOutput =
                    "With Find My Quote, you can get"
                            + " the movie name for your quote";

            String repromptText = "Which phrase do you have?";

            return newAskResponse(speechOutput, false, repromptText, false);
        } else if ("AMAZON.StopIntent".equals(intentName)) {
            PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
            outputSpeech.setText("Goodbye");

            return SpeechletResponse.newTellResponse(outputSpeech);
        } else if ("AMAZON.CancelIntent".equals(intentName)) {
            PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
            outputSpeech.setText("Goodbye");

            return SpeechletResponse.newTellResponse(outputSpeech);
        } else {
            throw new SpeechletException("Invalid Intent");
        }
    }

    @Override
    public void onSessionEnded(final SessionEndedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        // any session cleanup logic would go here
    }

    /**
     * Function to handle the onLaunch skill behavior.
     * 
     * @return SpeechletResponse object with voice/card response to return to the user
     */
    private SpeechletResponse getWelcomeResponse() {
        String speechOutput = "Find My Quote. What quote do you have in mind?";
        // If the user either does not reply to the welcome message or says something that is not
        // understood, they will be prompted again with this text.
        String repromptText =
                "With Find My Quote, you can get movie information for any quote you say. "
                        + " For example, you could say 'No, I am your Father'.";

        return newAskResponse(speechOutput, false, repromptText, false);
    }

    /**
     * Adding for future parsing logic.
     *
     * @param intent
     * @return
     */
    private String getPhrase(Intent intent) {
        if(intent == null)
        {
            return "null";
        }
        String phraseSlot = intent.getSlot(SLOT_PHRASE).getValue();
        return phraseSlot;
    }
    /**
     * Prepares the speech to reply to the user. Obtain movie from QuoDB for the quote/phrase specified
     * by the user and return those events in both
     * speech and SimpleCard format.
     * 
     * @param intent
     *            the intent object which contains the phrase slot
     * @param session
     *            the session object
     * @return SpeechletResponse object with voice/card response to return to the user
     */
    private SpeechletResponse handleFirstEventRequest(Intent intent, Session session) {
        String phrase = getPhrase(intent);
        if(!phrase.equals(null)) {

            String speechPrefixContent = "<p>For " + phrase + "</p> ";
            String cardPrefixContent = "For " + phrase + ", ";
            String cardTitle = "Movies for " + phrase;

            //replace spaces for path param request
            String apiReadyPhrase = phrase.replaceAll(" ", "%20");
            System.out.println("apiReadyPhrase: " + apiReadyPhrase);
            ArrayList<String> events = getJsonEventsFromQuoDB(apiReadyPhrase);
            String speechOutput = "";
            if (events.isEmpty()) {
                speechOutput =
                        "QuoDB could not find a movie for your quote. Sorry. ";

                // Create the plain text output
                SsmlOutputSpeech outputSpeech = new SsmlOutputSpeech();
                outputSpeech.setSsml("<speak>" + speechOutput + "</speak>");

                return SpeechletResponse.newTellResponse(outputSpeech);
            } else {
                StringBuilder speechOutputBuilder = new StringBuilder();
                speechOutputBuilder.append(speechPrefixContent);
                StringBuilder cardOutputBuilder = new StringBuilder();
                cardOutputBuilder.append(cardPrefixContent);
                for (int i = 0; i < PAGINATION_SIZE; i++) {
                    speechOutputBuilder.append("<p>");
                    speechOutputBuilder.append(events.get(i));
                    speechOutputBuilder.append("</p> ");
                    cardOutputBuilder.append(events.get(i));
                    cardOutputBuilder.append("\n");
                }
                speechOutputBuilder.append(" There more movies with a similar quote, would you like to get another movie?");
                cardOutputBuilder.append(" There more movies with a similar quote, would you like to get another movie?");
                speechOutput = speechOutputBuilder.toString();

                String repromptText =
                        "With Find My Quote, you can get movie information for any quote you say."
                                + " For example, you could say 'No, I am your father'";

                // Create the Simple card content.
                SimpleCard card = new SimpleCard();
                card.setTitle(cardTitle);
                card.setContent(cardOutputBuilder.toString());

                // After reading the first 3 events, set the count to 3 and add the events
                // to the session attributes
                session.setAttribute(SESSION_INDEX, PAGINATION_SIZE);
                session.setAttribute(SESSION_TEXT, events);

                SpeechletResponse response = newAskResponse("<speak>" + speechOutput + "</speak>", true, repromptText, false);
                response.setCard(card);
                return response;
            }
        }else{

           String speechOutput = "IM sorry, I was not able to understand your quote.";

            // Create the plain text output
            SsmlOutputSpeech outputSpeech = new SsmlOutputSpeech();
            outputSpeech.setSsml("<speak>" + speechOutput + "</speak>");
            return SpeechletResponse.newTellResponse(outputSpeech);
        }


    }

    /**
     * Prepares the speech to reply to the user. Obtains the list of movies as well as the current
     * index from the session attributes. After getting the next set of movies, increment the index
     * and store it back in session attributes. This allows us to obtain new movies without making
     * repeated network calls, by storing values (movies, index) during the interaction with the
     * user.
     * 
     * @param session
     *            object containing session attributes with events list and index
     * @return SpeechletResponse object with voice/card response to return to the user
     */
    private SpeechletResponse handleNextEventRequest(Session session) {
        String cardTitle = "Other movies with similar quotes";
        ArrayList<String> events = (ArrayList<String>) session.getAttribute(SESSION_TEXT);
        int index = (Integer) session.getAttribute(SESSION_INDEX);
        String speechOutput = "";
        String cardOutput = "";
        if (events == null) {
            speechOutput =
                    "With Find My Quote, you can get movie information for any quote you say."
                            + " For example, you could say 'No, I am your father'";
        } else if (index >= events.size()) {
            speechOutput =
                    "There are no more matching movies for this quote.";
        } else {
            StringBuilder speechOutputBuilder = new StringBuilder();
            StringBuilder cardOutputBuilder = new StringBuilder();
            for (int i = 0; i < PAGINATION_SIZE && index < events.size(); i++) {
                speechOutputBuilder.append("<p>");
                speechOutputBuilder.append(events.get(index));
                speechOutputBuilder.append("</p> ");
                cardOutputBuilder.append(events.get(index));
                cardOutputBuilder.append(" ");
                index++;
            }
            if (index < events.size()) {
                speechOutputBuilder.append(" Want to go deeper into movie awesomeness, and get more movies for this quote?");
                cardOutputBuilder.append(" Want to go deeper into movie awesomeness, and get more movies for this quote??");
            }
            session.setAttribute(SESSION_INDEX, index);
            speechOutput = speechOutputBuilder.toString();
            cardOutput = cardOutputBuilder.toString();
        }
        String repromptText = "Do you want to know more about what happened on this date?";

        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle(cardTitle);
        card.setContent(cardOutput.toString());

        SpeechletResponse response = newAskResponse("<speak>" + speechOutput + "</speak>", true, repromptText, false);
        response.setCard(card);
        return response;
    }

    /**
     * Download JSON-formatted list of movie information from QuoDB and return a
     * String array of the movie information, with each movie information String representing an element in the array.
     * 
     * @param phrase
     *            the quote phrase to get the movie information, example: No, I am your father.
     *
     * @return String array of String movie information for that quote, 1 movie per element of the array
     */
    private ArrayList<String> getJsonEventsFromQuoDB(String phrase) {
        InputStreamReader inputStream = null;
        BufferedReader bufferedReader = null;
        String text = "";
        try {
            String line;
            URL url = new URL(URL_PREFIX + phrase);
            inputStream = new InputStreamReader(url.openStream(), Charset.forName("US-ASCII"));
            bufferedReader = new BufferedReader(inputStream);
            StringBuilder builder = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line);
            }
            text = builder.toString();
        } catch (IOException e) {
            // reset text variable to a blank string
            text = "";
        } finally {
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(bufferedReader);
        }
        System.out.println(text);
        return parseMovieJson(text);
    }


    /**
     *Parse the information received from the API endpoint into readable movie information for Alexa to parse.
     *
     * @param text The text response received from QuoDB
     * @return The movie information including, the matched quote, movie title, and year the movie came out are return in an ArrayList of Strings.
     */
    private ArrayList<String> parseMovieJson(String text) {

        ArrayList<String> movies = new ArrayList<String>();

        if(text.isEmpty())
        {
            movies.add("I'm sorry, I could not find a movie for your quote. I am still learning. Please don't hate me...");
            return movies;
        }
        JSONObject obj = new JSONObject(text);

        JSONArray arr = obj.getJSONArray("docs");

        for (int i = 0; i < arr.length(); i++)
        {
            String movieText;
            String movieTitle = arr.getJSONObject(i).getString("title");
            String movieYear = Integer.toString(arr.getJSONObject(i).getInt("year"));
            String actualQuote = arr.getJSONObject(i).getString("phrase");
            if(i == 0) {
                movieText = "The quote is possibly, " + actualQuote
                        + ", from the movie " + movieTitle
                        + ", which came out in " + movieYear;
            }
            else{
                movieText = "Another matching quote is, " + actualQuote
                        + ", from the movie " + movieTitle
                        + ", which came out in " + movieYear;
            }
            movies.add(movieText);
        }
        return movies;
    }
    /**
     * Wrapper for creating the Ask response from the input strings.
     * 
     * @param stringOutput
     *            the output to be spoken
     * @param isOutputSsml
     *            whether the output text is of type SSML
     * @param repromptText
     *            the reprompt for if the user doesn't reply or is misunderstood.
     * @param isRepromptSsml
     *            whether the reprompt text is of type SSML
     * @return SpeechletResponse the speechlet response
     */
    private SpeechletResponse newAskResponse(String stringOutput, boolean isOutputSsml,
            String repromptText, boolean isRepromptSsml) {
        OutputSpeech outputSpeech, repromptOutputSpeech;
        if (isOutputSsml) {
            outputSpeech = new SsmlOutputSpeech();
            ((SsmlOutputSpeech) outputSpeech).setSsml(stringOutput);
        } else {
            outputSpeech = new PlainTextOutputSpeech();
            ((PlainTextOutputSpeech) outputSpeech).setText(stringOutput);
        }

        if (isRepromptSsml) {
            repromptOutputSpeech = new SsmlOutputSpeech();
            ((SsmlOutputSpeech) repromptOutputSpeech).setSsml(repromptText);
        } else {
            repromptOutputSpeech = new PlainTextOutputSpeech();
            ((PlainTextOutputSpeech) repromptOutputSpeech).setText(repromptText);
        }
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(repromptOutputSpeech);
        return SpeechletResponse.newAskResponse(outputSpeech, reprompt);
    }

}
