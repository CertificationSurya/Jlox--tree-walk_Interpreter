# Define Java compiler
JAVAC = javac

# Define Java interpreter
JAVA = java

# Define source directory
SRC_DIR = com/craftinginterpreters/lox

# GenerateAst directory
GEN_SRC_DIR = com/craftinginterpreters/tool

# Define output directory
OUT_DIR = com/craftinginterpreters/tempClassesFolder

# Define main class
MAIN_CLASS = com.craftinginterpreters.lox.Lox

# Gen-Class
GEN_MAIN_CLASS = com.craftinginterpreters.tool.GenerateAst

# Define Java source files
SRCS = $(wildcard $(SRC_DIR)/Lox.java)
GEN_SRC = $(GEN_SRC_DIR)/GenerateAst.java

# Define Java class files
CLASSES = $(patsubst $(SRC_DIR)/%.java,$(OUT_DIR)/%.class,$(SRCS))

# GenClass
GEN_CLASSES = $(GEN_SRC_OUT_DIR)/GenerateAst.class

# Default target: compile the Java program
all: $(CLASSES)

# Compile Java source files into class files
$(OUT_DIR)/%.class: $(SRC_DIR)/%.java
	@mkdir -p $(OUT_DIR)
	$(JAVAC) -d $(OUT_DIR) $<


# Compile GenerateAst.java into class file
$(GEN_SRC_DIR)/GenerateAst.class: $(GEN_SRC)
	@mkdir -p $(OUT_DIR)
	$(JAVAC) -d $(GEN_SRC_DIR) $<

# Generate AST classes
genrun: $(GEN_SRC_DIR)/GenerateAst.class
	$(JAVA) -cp $(GEN_SRC_DIR) $(GEN_MAIN_CLASS) $(SRC_DIR)

# Run the Java program
run: $(CLASSES)
	$(JAVA) -cp $(OUT_DIR) $(MAIN_CLASS)

# Clean compiled class files
clean:
	@rm -rf $(OUT_DIR)
	@rm -rf $(GEN_SRC_DIR)/com
