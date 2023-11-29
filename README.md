# SpringApiDocGenerator

This project represents my personal trial to come up with a way to automatically generate **proper** REST-API documentation.

What I mean by that is documentation that's fully aware of not only all input types (their origin, validation, name and schema) and all response types, but also all possible status-codes, including their meaning. The fact that all of this logic is encoded within the resulting fat jar of a Spring project, but I still have to go about hand-rolling my own documentation separately or be content with lacking, robot-looking and repetitive auto-generated YAMLs is totally unacceptable.

As this codebase currently reflects a playground for my ideas, it is of *horrible* design. You have been warned.