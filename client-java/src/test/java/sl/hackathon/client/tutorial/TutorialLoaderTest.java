package sl.hackathon.client.tutorial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TutorialLoaderTest {
    @Test
    void loadsBasicsTutorial() throws Exception {
        TutorialDefinition def = TutorialLoader.load("basics-01");
        assertNotNull(def);
        assertNotNull(def.map());
        assertNotNull(def.map().dimension());
        assertTrue(def.foodScarcity() >= 0.0f && def.foodScarcity() <= 1.0f);
        assertNotNull(def.gameEnd());
        assertNotNull(def.gameEnd().type());
    }

    @Test
    void loadsSecondBasicsTutorial() throws Exception {
        TutorialDefinition def = TutorialLoader.load("basics-02");
        assertNotNull(def);
        assertNotNull(def.map());
        assertNotNull(def.map().dimension());
        assertTrue(def.foodScarcity() >= 0.0f && def.foodScarcity() <= 1.0f);
        assertNotNull(def.gameEnd());
        assertNotNull(def.gameEnd().type());
    }

    @Test
    void loadsThirdBasicsTutorial() throws Exception {
        TutorialDefinition def = TutorialLoader.load("basics-03");
        assertNotNull(def);
        assertNotNull(def.map());
        assertNotNull(def.map().dimension());
        assertTrue(def.foodScarcity() >= 0.0f && def.foodScarcity() <= 1.0f);
        assertNotNull(def.gameEnd());
        assertNotNull(def.gameEnd().type());
    }

    @Test
    void missingTutorialThrows() {
        Exception ex = assertThrows(Exception.class, () -> TutorialLoader.load("does-not-exist"));
        assertTrue(ex.getMessage().contains("Tutorial resource not found"));
    }
}
