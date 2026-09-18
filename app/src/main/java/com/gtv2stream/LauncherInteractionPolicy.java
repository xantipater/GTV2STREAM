package com.gtv2stream;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Distinguishes an unreadable recommendation from an explicit non-content action. */
final class LauncherInteractionPolicy {
    private LauncherInteractionPolicy() { }

    static final class Assessment {
        final boolean ignore;
        final boolean hasCard;
        final boolean enterEdit;
        final boolean exitEdit;

        Assessment(boolean ignore, boolean hasCard, boolean enterEdit, boolean exitEdit) {
            this.ignore = ignore;
            this.hasCard = hasCard;
            this.enterEdit = enterEdit;
            this.exitEdit = exitEdit;
        }
    }

    /** Main-thread interaction state; workers only read the volatile generation. */
    static final class Session {
        private boolean editing;
        private volatile long generation;

        boolean accept(Assessment interaction, boolean clicked) {
            if (interaction.enterEdit) editing = true;
            else if (clicked && interaction.exitEdit) editing = false;
            if (interaction.ignore || (editing && !interaction.hasCard)) {
                invalidate();
                return false;
            }
            if (interaction.hasCard) editing = false;
            return true;
        }

        void beginEditing() { editing = true; invalidate(); }
        void returnHome() { editing = false; invalidate(); }
        void showDetail() { editing = false; }
        void invalidate() { generation++; }
        boolean isEditing() { return editing; }
        long ticket() { return generation; }
        boolean isCurrent(long ticket) { return ticket == generation; }
    }

    /**
     * Only the title slot is checked in event lists; later rows can legitimately
     * contain Synopsis or other metadata. Provider-first cards use slot two.
     * Descriptions and node values belong to the event source, never its neighbours.
     */
    static Assessment assess(List<CharSequence> eventText, String description,
            String nodeText, String nodeDescription, Set<String> appLabels) {
        String primary = "";
        if (eventText != null && !eventText.isEmpty()) {
            int index = eventText.size() > 1
                    && RecommendationTitleParser.isProviderLoose(string(eventText.get(0))) ? 1 : 0;
            primary = string(eventText.get(index));
        }
        // A provider label on a child can accompany a genuine provider-bearing
        // card. A bare YouTube/Netflix app tile has no such card evidence.
        boolean card = RecommendationTitleParser.fromEventTextSource(eventText).hasProvider()
                || RecommendationTitleParser.fromDescriptionSource(description).hasProvider()
                || RecommendationTitleParser.fromDescriptionSource(nodeDescription).hasProvider();
        boolean ignore = blocks(primary, appLabels, card) || blocks(description, appLabels, card)
                || blocks(nodeText, appLabels, card) || blocks(nodeDescription, appLabels, card);
        boolean enterEdit = editEntry(primary) || editEntry(description)
                || editEntry(nodeText) || editEntry(nodeDescription);
        boolean exitEdit = editExit(primary) || editExit(description)
                || editExit(nodeText) || editExit(nodeDescription);
        return new Assessment(ignore, card, enterEdit, exitEdit);
    }

    private static boolean editEntry(String value) {
        String label = value == null ? "" : value.trim().toLowerCase(Locale.US);
        return label.equals("move") || label.equals("rearrange") || label.equals("reorder")
                || label.equals("rearrange apps") || label.equals("reorder apps")
                || label.equals("move app") || label.equals("move apps")
                || label.equals("move left") || label.equals("move right")
                || label.equals("move up") || label.equals("move down")
                || label.equals("move to top") || label.equals("move to front");
    }

    private static boolean editExit(String value) {
        String label = value == null ? "" : value.trim().toLowerCase(Locale.US);
        return label.equals("done") || label.equals("cancel") || label.equals("back");
    }

    private static boolean blocks(String value, Set<String> appLabels, boolean card) {
        if (RecommendationTitleParser.isNonContentControl(value)) return true;
        if (card && RecommendationTitleParser.isProviderLoose(value)) return false;
        return AppLabelPolicy.matches(appLabels, value);
    }

    private static String string(CharSequence value) {
        return value == null ? "" : value.toString();
    }
}
