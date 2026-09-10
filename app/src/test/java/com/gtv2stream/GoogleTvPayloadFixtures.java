package com.gtv2stream;

import java.util.Arrays;
import java.util.List;

/** Deterministic, non-user Google TV payload examples used by parser regressions. */
final class GoogleTvPayloadFixtures {
    static final class Fixture {
        final String name;
        final List<CharSequence> eventText;
        final String description;
        final String title;
        final boolean youtube;

        Fixture(String name, List<CharSequence> eventText, String description,
                String title, boolean youtube) {
            this.name = name;
            this.eventText = eventText;
            this.description = description;
            this.title = title;
            this.youtube = youtube;
        }

        static Fixture description(String name, String raw, String title, boolean youtube) {
            return new Fixture(name, null, raw, title, youtube);
        }

        static Fixture event(String name, List<CharSequence> raw, String title, boolean youtube) {
            return new Fixture(name, raw, null, title, youtube);
        }
    }

    private GoogleTvPayloadFixtures() { }

    static List<Fixture> all() {
        return Arrays.asList(
                Fixture.description("normal movie", "Dune (2021)", "Dune", false),
                Fixture.description("normal TV", "The Bear (2023)", "The Bear", false),
                Fixture.event("provider first", Arrays.asList("Netflix", "The Bear", "Drama"), "The Bear", false),
                Fixture.description("provider last", "The Bear — Netflix", "The Bear", false),
                Fixture.description("YouTube action", "Big Buck Bunny. Watch on YouTube", "Big Buck Bunny", true),
                Fixture.event("advertisement", Arrays.asList("Advertisement", "Dune"), "", false),
                Fixture.description("sponsored card", "Sponsored. Dune. Watch on Netflix", "", false),
                Fixture.description("settings chrome", "Network & Internet", "", false),
                Fixture.description("malformed payload", "Watch on Netflix", "", false),
                Fixture.description("ambiguous prose", "A detective investigates a conspiracy. Watch on ITVX", "", false)
        );
    }
}
