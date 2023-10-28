Feature: Default middleware behavior

  Scenario:
    Given a default middleware
    When a message is passed
    Then message handler generates response
