Feature: Running MLLP server

  Scenario: Basic send - reply
    Given an MLLP server
    And an MLLP client
    When the client sends a message
    Then gets a response from the server