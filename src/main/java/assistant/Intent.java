package assistant;

/** The 60 MASSIVE intents, plus {@link #UNSUPPORTED} for requests none of them covers. */
public enum Intent {
    ALARM_QUERY("Ask which alarms are set"),
    ALARM_REMOVE("Cancel or delete an alarm"),
    ALARM_SET("Set an alarm or a wake-up call"),
    AUDIO_VOLUME_DOWN("Turn the volume down"),
    AUDIO_VOLUME_MUTE("Mute the sound or ask for silence"),
    AUDIO_VOLUME_OTHER("Other volume settings"),
    AUDIO_VOLUME_UP("Turn the volume up"),
    CALENDAR_QUERY("Ask about events, meetings or reminders in the calendar"),
    CALENDAR_REMOVE("Delete an event or a reminder from the calendar"),
    CALENDAR_SET("Add an event, a meeting or a reminder to the calendar"),
    COOKING_QUERY("A question about cooking, ingredients or kitchen appliances"),
    COOKING_RECIPE("Ask for a recipe or how to cook a dish"),
    DATETIME_CONVERT("Convert a time between time zones"),
    DATETIME_QUERY("Ask the current time or date, or the time somewhere"),
    EMAIL_ADDCONTACT("Add an email contact"),
    EMAIL_QUERY("Check or read emails"),
    EMAIL_QUERYCONTACT("Ask for a contact's details"),
    EMAIL_SENDEMAIL("Write, send or reply to an email"),
    GENERAL_GREET("A greeting or small talk"),
    GENERAL_JOKE("Ask for a joke"),
    GENERAL_QUIRKY("An odd, chatty or off-topic remark to the assistant"),
    IOT_CLEANING("Start the vacuum or cleaning robot"),
    IOT_COFFEE("Make a coffee"),
    IOT_HUE_LIGHTCHANGE("Change the colour of the lights"),
    IOT_HUE_LIGHTDIM("Dim the lights"),
    IOT_HUE_LIGHTOFF("Turn the lights off"),
    IOT_HUE_LIGHTON("Turn the lights on"),
    IOT_HUE_LIGHTUP("Make the lights brighter"),
    IOT_WEMO_OFF("Switch a smart plug or appliance off"),
    IOT_WEMO_ON("Switch a smart plug or appliance on"),
    LISTS_CREATEORADD("Create a list or add an item to a list"),
    LISTS_QUERY("Ask what is on a list"),
    LISTS_REMOVE("Remove an item from a list or delete a list"),
    MUSIC_DISLIKENESS("Say a song or artist is disliked"),
    MUSIC_LIKENESS("Say a song or artist is liked"),
    MUSIC_QUERY("Ask what song is playing or about music"),
    MUSIC_SETTINGS("Music playback settings such as shuffle or repeat"),
    NEWS_QUERY("Ask for the news"),
    PLAY_AUDIOBOOK("Play or resume an audiobook"),
    PLAY_GAME("Play a game"),
    PLAY_MUSIC("Play a song, an artist, an album or a playlist"),
    PLAY_PODCASTS("Play a podcast"),
    PLAY_RADIO("Play a radio station"),
    QA_CURRENCY("Exchange rates and currency conversion"),
    QA_DEFINITION("Ask what a word means"),
    QA_FACTOID("A general knowledge question"),
    QA_MATHS("A calculation"),
    QA_STOCK("Stock prices"),
    RECOMMENDATION_EVENTS("Recommend events or things to do"),
    RECOMMENDATION_LOCATIONS("Recommend places such as restaurants or shops"),
    RECOMMENDATION_MOVIES("Recommend movies"),
    SOCIAL_POST("Post on social media or complain to a company"),
    SOCIAL_QUERY("Check social media updates"),
    TAKEAWAY_ORDER("Order food for delivery or takeaway"),
    TAKEAWAY_QUERY("Ask about a takeaway order or a restaurant's delivery"),
    TRANSPORT_QUERY("Ask about trains, buses or directions"),
    TRANSPORT_TAXI("Book a taxi or a ride"),
    TRANSPORT_TICKET("Book a train or plane ticket"),
    TRANSPORT_TRAFFIC("Ask about the traffic"),
    WEATHER_QUERY("Ask about the weather"),
    UNSUPPORTED("A request that none of the other options covers");

    final String description;

    Intent(String description) {
        this.description = description;
    }

    public String label() {
        return name().toLowerCase();
    }

    static Intent fromLabel(String label) {
        return valueOf(label.toUpperCase());
    }
}
