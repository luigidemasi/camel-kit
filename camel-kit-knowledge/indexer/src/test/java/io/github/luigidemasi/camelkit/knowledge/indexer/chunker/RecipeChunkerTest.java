package io.github.luigidemasi.camelkit.knowledge.indexer.chunker;

import io.github.luigidemasi.camelkit.knowledge.indexer.chunker.RecipeChunker.Recipe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipeChunkerTest {

    private final RecipeChunker chunker = new RecipeChunker();

    @Test
    void parseChangeTypeRecipe() {
        String yaml = """
                type: specs.openrewrite.org/v1beta/recipe
                name: org.apache.camel.upgrade.CamelMigrationRecipe
                recipeList:
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.apache.camel.component.http4.HttpComponent
                      newFullyQualifiedTypeName: org.apache.camel.component.http.HttpComponent
                """;

        List<Recipe> recipes = chunker.chunk(yaml);

        assertEquals(1, recipes.size());
        Recipe recipe = recipes.get(0);
        assertEquals("ChangeType", recipe.recipeType());
        assertEquals("org.apache.camel.component.http4.HttpComponent", recipe.oldName());
        assertEquals("org.apache.camel.component.http.HttpComponent", recipe.newName());
        assertEquals("http4", recipe.componentName());
    }

    @Test
    void parseMultipleRecipes() {
        String yaml = """
                type: specs.openrewrite.org/v1beta/recipe
                name: test
                recipeList:
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.apache.camel.component.http4.HttpComponent
                      newFullyQualifiedTypeName: org.apache.camel.component.http.HttpComponent
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.apache.camel.component.netty4.NettyComponent
                      newFullyQualifiedTypeName: org.apache.camel.component.netty.NettyComponent
                """;

        List<Recipe> recipes = chunker.chunk(yaml);
        assertEquals(2, recipes.size());
        assertEquals("http4", recipes.get(0).componentName());
        assertEquals("netty4", recipes.get(1).componentName());
    }

    @Test
    void emptyRecipeList() {
        String yaml = """
                type: specs.openrewrite.org/v1beta/recipe
                name: empty
                recipeList: []
                """;

        List<Recipe> recipes = chunker.chunk(yaml);
        assertTrue(recipes.isEmpty());
    }

    @Test
    void parseMultiDocumentYaml() {
        String yaml = """
                type: specs.openrewrite.org/v1beta/recipe
                name: org.apache.camel.upgrade.camel40.ChangeTypes
                recipeList:
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.apache.camel.support.IntrospectionSupport
                      newFullyQualifiedTypeName: org.apache.camel.impl.engine.IntrospectionSupport
                ---
                type: specs.openrewrite.org/v1beta/recipe
                name: org.apache.camel.upgrade.camel40.ChangeMBeans
                recipeList:
                  - org.openrewrite.java.ChangeMethodName:
                      methodPattern: org.apache.camel.api.management.mbean.ManagedChoiceMBean choiceStatistics()
                      newMethodName: extendedInformation
                """;

        List<Recipe> recipes = chunker.chunk(yaml);
        assertEquals(2, recipes.size());
        assertEquals("ChangeType", recipes.get(0).recipeType());
        assertEquals("ChangeMethodName", recipes.get(1).recipeType());
    }

    @Test
    void skipDocumentsWithoutRecipeList() {
        String yaml = """
                type: specs.openrewrite.org/v1beta/category
                name: Camel 3.x
                packageName: org.apache.camel.upgrade30
                description: Migrate from Camel 3.x to 4.x.
                ---
                type: specs.openrewrite.org/v1beta/recipe
                name: test
                recipeList:
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.apache.camel.component.http4.HttpComponent
                      newFullyQualifiedTypeName: org.apache.camel.component.http.HttpComponent
                """;

        List<Recipe> recipes = chunker.chunk(yaml);
        assertEquals(1, recipes.size());
        assertEquals("http4", recipes.get(0).componentName());
    }

    @Test
    void invalidYaml() {
        List<Recipe> recipes = chunker.chunk("not a map");
        assertTrue(recipes.isEmpty());
    }
}
