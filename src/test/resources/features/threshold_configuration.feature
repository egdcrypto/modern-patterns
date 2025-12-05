@thresholds
Feature: Dimensional Threshold Configuration
  As a narrative engine administrator
  I want to configure thresholds at different dimensional levels
  So that each world, region, and location can have appropriate settings

  Background:
    Given the threshold service is initialized with default settings
    And the following world types exist:
      | worldType | minConfidence | maxCharactersPerScene | dangerLevel |
      | FANTASY   | 0.7           | 15                    | MEDIUM      |
      | HORROR    | 0.8           | 10                    | HIGH        |
      | SCI_FI    | 0.7           | 20                    | LOW         |

  # ==========================================
  # Threshold Inheritance
  # ==========================================

  @inheritance
  Scenario: Thresholds inherit from parent dimensions
    Given a world "middle-earth" of type "FANTASY"
    And a region "shire" in world "middle-earth"
    And a location "bag-end" in region "shire"
    When I query thresholds for location "bag-end"
    Then the threshold "maxCharactersPerScene" should be 15
    And the threshold "dangerLevel" should be "MEDIUM"

  @override
  Scenario: Child dimensions can override parent thresholds
    Given a world "middle-earth" of type "FANTASY"
    And a region "mordor" in world "middle-earth"
    And the region "mordor" has threshold "dangerLevel" set to "EXTREME"
    And a location "mount-doom" in region "mordor"
    When I query thresholds for location "mount-doom"
    Then the threshold "dangerLevel" should be "EXTREME"
    And the threshold "maxCharactersPerScene" should be 15

  @world-override
  Scenario: World-specific overrides take precedence over world type
    Given a world "child-friendly-fantasy" of type "FANTASY"
    And the world "child-friendly-fantasy" has threshold "dangerLevel" set to "SAFE"
    And a region "peaceful-meadow" in world "child-friendly-fantasy"
    And a location "flower-garden" in region "peaceful-meadow"
    When I query thresholds for location "flower-garden"
    Then the threshold "dangerLevel" should be "SAFE"

  # ==========================================
  # AI Confidence Thresholds
  # ==========================================

  @confidence
  Scenario Outline: AI responses must meet confidence threshold
    Given a world "<world>" of type "<worldType>"
    And a region "test-region" in world "<world>"
    And a location "test-location" in region "test-region"
    When an AI response has confidence <confidence>
    Then the response <result> meet the confidence threshold for "test-location"

    Examples:
      | world       | worldType | confidence | result         |
      | fantasy-1   | FANTASY   | 0.75       | should         |
      | fantasy-2   | FANTASY   | 0.65       | should not     |
      | horror-1    | HORROR    | 0.85       | should         |
      | horror-2    | HORROR    | 0.75       | should not     |
      | scifi-1     | SCI_FI    | 0.70       | should         |

  # ==========================================
  # Character Limits
  # ==========================================

  @character-limits
  Scenario: Enforce maximum characters per scene
    Given a world "crowded-world" of type "FANTASY"
    And a region "busy-region" in world "crowded-world"
    And a location "town-square" in region "busy-region"
    And the location "town-square" currently has 14 characters
    When I check if another character can be added to "town-square"
    Then the result should be true
    When the location "town-square" has 15 characters
    And I check if another character can be added to "town-square"
    Then the result should be false

  @custom-character-limit
  Scenario: Location can have custom character limit
    Given a world "intimate-world" of type "FANTASY"
    And a region "small-region" in world "intimate-world"
    And a location "private-chamber" in region "small-region"
    And the location "private-chamber" has threshold "maxCharactersPerScene" set to 3
    When I check if another character can be added to "private-chamber" with 3 existing
    Then the result should be false

  # ==========================================
  # Event Triggers
  # ==========================================

  @event-triggers
  Scenario: Random events trigger based on probability threshold
    Given a world "eventful-world" of type "HORROR"
    And a region "haunted-region" in world "eventful-world"
    And a location "haunted-house" in region "haunted-region"
    # Horror has 0.25 probability
    When a random value of 0.20 is generated
    Then a random event should trigger at "haunted-house"
    When a random value of 0.30 is generated
    Then a random event should not trigger at "haunted-house"

  # ==========================================
  # Effective Configuration
  # ==========================================

  @effective-config
  Scenario: Get complete effective configuration for a location
    Given a world "test-world" of type "FANTASY"
    And a region "test-region" in world "test-world"
    And a location "test-location" in region "test-region"
    When I request effective thresholds for "test-location"
    Then the effective configuration should contain:
      | key                     | value    |
      | minConfidence           | 0.7      |
      | maxCharactersPerScene   | 15       |
      | dangerLevel             | MEDIUM   |
      | eventTriggerProbability | 0.15     |
      | contentModerationLevel  | STANDARD |
