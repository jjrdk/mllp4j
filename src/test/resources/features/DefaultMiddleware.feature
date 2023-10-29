Feature: Default middleware behavior

  Scenario: Default message handling
    Given a default middleware
    When a message is passed
    Then message handler generates response
