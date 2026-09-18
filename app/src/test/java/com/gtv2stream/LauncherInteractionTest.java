package com.gtv2stream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Regression inputs are synthetic boundaries based on issues #15 and #16, not TV captures. */
public final class LauncherInteractionTest {
    public static void main(String[] args) throws Exception {
        System.out.println("LauncherInteractionTest: PASS (" + run() + " assertions)");
    }

    static int run() throws Exception {
        int assertions = 0;
        Set<String> labels = AppLabelPolicy.normalizeAll(Arrays.asList(
                "MiX", "MiX Xplorer", "MiXplorer", "YouTube", "Netflix"));
        for (String app : Arrays.asList("MiX", "MiX Xplorer", "MiXplorer", " YouTube ", "NETFLIX")) {
            check(ignored(List.of(app), "", "", "", labels), "app event: " + app); assertions++;
            check(ignored(List.of(), app, "", "", labels), "app description: " + app); assertions++;
            check(ignored(List.of("Column 3"), "", app, "", labels), "app node: " + app); assertions++;
            check(ignored(List.of(), "", "", app, labels), "app node description: " + app); assertions++;
        }
        for (String control : Arrays.asList("Move", "Rearrange", "Rearrange Apps", "Move Left",
                "Move Right", "Done", "Cancel", "Settings", "Your Apps")) {
            check(ignored(List.of(control), "", "", "", labels), "control click: " + control); assertions++;
            check(ignored(List.of("Column 3"), "", "", control, labels),
                    "control on source node: " + control); assertions++;
            check(RecommendationTitleParser.youtubeSource(control).isEmpty(),
                    "control cannot become an ambient YouTube title: " + control); assertions++;
        }
        check(ignored(List.of("YouTube", "Move"), "", "", "", labels),
                "provider-first control is terminal too"); assertions++;
        check(!ignored(List.of("Dune"), "", "", "", labels), "normal movie title"); assertions++;
        check(!ignored(List.of("MiX Tape"), "", "", "", labels), "app-name prefix remains a title"); assertions++;
        check(!ignored(List.of("Moving On"), "", "", "", labels), "edit-verb prefix remains a title"); assertions++;
        check(!ignored(List.of("Dune", "Synopsis", "Watch on Netflix"), "", "", "", labels),
                "secondary metadata is not a control click"); assertions++;
        check(!ignored(List.of("Netflix", "Dune"), "", "Netflix", "", labels),
                "provider-first film is not the Netflix tile"); assertions++;
        check(!ignored(List.of("YouTube", "Big Buck Bunny"), "", "YouTube", "", labels),
                "provider-first video is not the YouTube tile"); assertions++;
        check(!ignored(List.of("Column 3"), "Big Buck Bunny, YouTube • Blender", "YouTube", "", labels),
                "rich video description preserves card evidence"); assertions++;
        for (String primary : Arrays.asList("YouTube", "Column 3")) {
            LauncherInteractionPolicy.Assessment nodeCard = LauncherInteractionPolicy.assess(
                    List.of(primary), "", "YouTube", "Big Buck Bunny, YouTube • Blender", labels);
            check(!nodeCard.ignore && nodeCard.hasCard,
                    "node-only card evidence permits redirect and edit-mode recovery: " + primary); assertions++;
        }
        check(!ignored(List.of("Column 3"), "", "", "", labels),
                "position-only card can still use fallback outside edit mode"); assertions++;
        check(!ignored(Collections.emptyList(), "", "", "", labels),
                "missing payload is distinct from rejected control"); assertions++;
        check(!ignored(null, null, null, null, labels), "null payload is safe"); assertions++;
        for (String action : Arrays.asList("Watch", "Watch Now", "Play", "Trailer")) {
            check(!ignored(List.of(action), "", "", "", labels),
                    "entity action can still reach its detail title: " + action); assertions++;
        }
        check(RecommendationTitleParser.fromDescription("Sponsored. Dune. Watch on Netflix").isEmpty(),
                "sponsored rejection preserved"); assertions++;
        check(RecommendationTitleParser.fromDescription("Advertisement. Big Buck Bunny. Watch on YouTube").isEmpty(),
                "advertisement rejection preserved"); assertions++;

        // Exercise the production interaction state across a sequence, not just
        // isolated labels. A ticket represents work queued before the next event.
        LauncherInteractionPolicy.Assessment video = LauncherInteractionPolicy.assess(
                List.of("YouTube"), "", "YouTube", "Big Buck Bunny, YouTube • Blender", labels);
        LauncherInteractionPolicy.Assessment position = LauncherInteractionPolicy.assess(
                List.of("Column 3"), "", "", "", labels);
        LauncherInteractionPolicy.Assessment done = LauncherInteractionPolicy.assess(
                List.of("Done"), "", "", "", labels);
        for (String entry : Arrays.asList("Move", "Rearrange", "Rearrange Apps", "long press")) {
            LauncherInteractionPolicy.Session session = new LauncherInteractionPolicy.Session();
            check(session.accept(video, false), "initial video focus: " + entry); assertions++;
            long queued = session.ticket();
            if (entry.equals("long press")) session.beginEditing();
            else {
                check(!session.accept(LauncherInteractionPolicy.assess(
                        List.of(entry), "", "", "", labels), true), "edit entry rejected: " + entry);
                assertions++;
            }
            check(session.isEditing() && !session.isCurrent(queued),
                    "edit entry cancels queued or in-flight redirect: " + entry); assertions++;
            check(!session.accept(position, false) && !session.accept(position, true),
                    "edit position events cannot restart polling or fallback: " + entry); assertions++;
            check(!session.accept(done, false) && session.isEditing(),
                    "focusing Done does not exit editing: " + entry); assertions++;
            check(!session.accept(done, true) && !session.isEditing(),
                    "clicking Done ends edit mode without a redirect: " + entry); assertions++;
            check(session.accept(position, false), "fallback can recover after Done: " + entry); assertions++;
            session.beginEditing();
            check(session.accept(video, false) && !session.isEditing(),
                    "source-node-only video recovers without a Home event: " + entry); assertions++;
        }
        LauncherInteractionPolicy.Session appSession = new LauncherInteractionPolicy.Session();
        check(appSession.accept(video, true), "video click queues a redirect"); assertions++;
        long pending = appSession.ticket();
        check(!appSession.accept(LauncherInteractionPolicy.assess(
                List.of("MiX"), "", "", "", labels), true) && !appSession.isCurrent(pending),
                "opening an app cancels previous lookup and reassert tickets"); assertions++;
        appSession.beginEditing();
        appSession.returnHome();
        check(!appSession.isEditing() && appSession.accept(position, false),
                "returning Home restores position-only cards"); assertions++;
        appSession.beginEditing();
        appSession.showDetail();
        check(!appSession.isEditing(), "authoritative entity window ends editing"); assertions++;
        long current = appSession.ticket();
        check(appSession.accept(video, true) && !appSession.isCurrent(current),
                "a new explicit card selection supersedes the previous lookup"); assertions++;
        appSession.invalidate();
        check(!appSession.isCurrent(current), "service destruction invalidates pending launches"); assertions++;

        // The label guard is ineffective on Android 11+ if either kind of app
        // tile is invisible. Parse the actual manifest rather than matching comments.
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var document = factory.newDocumentBuilder().parse(sourcePath("app/src/main/AndroidManifest.xml").toFile());
        Element queries = (Element) document.getElementsByTagName("queries").item(0);
        for (String category : Arrays.asList("android.intent.category.LEANBACK_LAUNCHER",
                "android.intent.category.LAUNCHER")) {
            boolean visible = false;
            NodeList intents = queries.getElementsByTagName("intent");
            for (int i = 0; i < intents.getLength(); i++) {
                Element intent = (Element) intents.item(i);
                visible |= hasNamedChild(intent, "action", "android.intent.action.MAIN")
                        && hasNamedChild(intent, "category", category);
            }
            check(visible, "launchable app visibility: " + category); assertions++;
        }
        NodeList permissions = document.getElementsByTagName("uses-permission");
        for (int i = 0; i < permissions.getLength(); i++) {
            check(!"android.permission.QUERY_ALL_PACKAGES".equals(
                    ((Element) permissions.item(i)).getAttributeNS(ANDROID, "name")),
                    "app discovery stays scoped"); assertions++;
        }
        return assertions;
    }

    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    private static boolean hasNamedChild(Element parent, String tag, String name) {
        NodeList nodes = parent.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            if (name.equals(((Element) nodes.item(i)).getAttributeNS(ANDROID, "name"))) return true;
        }
        return false;
    }

    private static Path sourcePath(String relative) {
        Path root = Path.of(System.getProperty("user.dir"));
        if (Files.exists(root.resolve(relative))) return root.resolve(relative);
        return root.getParent().resolve(relative);
    }

    private static boolean ignored(List<CharSequence> text, String description,
            String nodeText, String nodeDescription, Set<String> labels) {
        return LauncherInteractionPolicy.assess(text, description, nodeText, nodeDescription, labels).ignore;
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
