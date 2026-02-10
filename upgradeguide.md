# Serenity BDD + Java 17 Upgrade Agent Instructions
## Goal
Upgrade this Serenity BDD project to the **latest stable version** (target: 5.2.2 or newer from https://github.com/serenity-bdd/serenity-core/releases).
**Also migrate the entire project to Java 17** as the baseline compiler/source/target level (required by Serenity 4.0+ / 5.x).

## Steps to Perform (in strict order)
1. **Determine latest Serenity version**  
   - Check https://github.com/serenity-bdd/serenity-core/releases/latest or Maven Central.  
   - Use terminal if needed: `curl -s https://api.github.com/repos/serenity-bdd/serenity-core/releases/latest | grep tag_name`  
   - Set as target (e.g. 5.2.2).

2. **Migrate to Java 17**  
   - Update `pom.xml`:
     - Set `<java.version>17</java.version>` (if using spring-boot-starter-parent or properties).
     - Or configure `maven-compiler-plugin`:
       ```xml
       <plugin>
         <groupId>org.apache.maven.plugins</groupId>
         <artifactId>maven-compiler-plugin</artifactId>
         <version>3.13.0</version> <!-- latest -->
         <configuration>
           <source>17</source>
           <target>17</target>
           <!-- Optional: --enable-preview if using previews, but avoid unless needed -->
         </configuration>
       </plugin>
       ```
     - If using `properties`: `<maven.compiler.source>17</maven.compiler.source>` and `<maven.compiler.target>17</maven.compiler.target>`.
   - If project uses older plugins/libs incompatible with 17, flag them (e.g. older AspectJ, certain bytecode tools).
   - Update any `JAVA_HOME` references or run configurations in IntelliJ to JDK 17.

3. **Update Serenity & Related Dependencies**  
   - Set `<serenity.version>5.2.2</serenity.version>` (or latest).  
   - Update all `net.serenity-bdd:*` artifacts to `${serenity.version}`.  
   - Replace `serenity-junit` → `serenity-junit5`.  
   - Remove old `junit` 4.x; add/upgrade to:
     ```xml
     <dependency>
       <groupId>org.junit.jupiter</groupId>
       <artifactId>junit-jupiter</artifactId>
       <version>5.10.3</version> <!-- or latest 5.x / 6.x if compatible -->
       <scope>test</scope>
     </dependency>
     ```
   - Update `maven-surefire-plugin` & `maven-failsafe-plugin` to 3.2.5+ (strong JUnit 5 support).  
   - If using Cucumber: upgrade to latest 7.x (e.g. 7.33.0+), ensure `serenity-cucumber` matches.

4. **Code & Annotation Migration (JUnit 4 → 5 + Serenity changes)**  
   - Replace `@RunWith(SerenityRunner.class)` → `@ExtendWith(SerenityJUnit5Extension.class)`.  
   - Update imports: `net.serenitybdd.junit5.SerenityJUnit5Extension`.  
   - `@Before`/`@After` → `@BeforeEach`/`@AfterEach`.  
   - Fix any old `net.thucydides` → `net.serenitybdd` packages.  
   - For Cucumber runners: migrate to JUnit Platform (`@Suite`, `@SelectClasspathResource`, etc.) if needed.  
   - Scan for deprecated APIs from release notes (e.g. session handling, REST assured changes).

5. **Verification & Fixes**  
   - Run `mvn clean compile` → fix compilation errors (Java 17 stricter rules, removed APIs).  
   - Run `mvn clean verify` or `mvn serenity:aggregate` → fix runtime/test failures iteratively.  
   - Check Serenity reports for issues.

6. **Git Workflow**  
   - Create branch: `upgrade-serenity-5.x-java17`.  
   - Commit changes with clear messages.  
   - Propose PR with summary of changes.

## Safety & Best Practices
- **Dry-run first**: Propose all diffs; apply only after review.
- Flag any high-risk areas (custom extensions, reflection-heavy code, old Selenium).
- Backup `pom.xml` and key test files before bulk edits.
- If Java 17 causes issues (e.g. illegal reflective access), add JVM args like `--add-opens` temporarily.
- Reference: https://serenity-bdd.github.io/docs/tutorials/migrating_to_serenity_4 (adapt for 5.x)

Start by confirming the exact latest Serenity version, then proceed step-by-step.