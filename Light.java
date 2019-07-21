import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import tester.*;
import javalib.impworld.*;
import java.awt.Color;
import javalib.worldimages.*;
import java.util.Random;


class GamePiece {
  // in logical coordinates, with the origin
  // at the top-left corner of the screen
  int row;
  int col;
  // whether this GamePiece is connected to the
  // adjacent left, right, top, or bottom pieces
  boolean left;
  boolean right;
  boolean top;
  boolean bottom;
  // whether the power station is on this piece
  boolean powerStation;
  int size = 40;
  ArrayList<GamePiece> neighbors;
  boolean isPowered; // does this piece have power?
  int distance;
  Color color;

  // Test Constructor 
  GamePiece(int row, int col, boolean left, boolean right, 
      boolean top, boolean bottom, boolean powerStation, boolean isPowered) {
    this.row = row;
    this.col = col;
    this.left = left;
    this.right = right;
    this.top = top;
    this.bottom = bottom;
    this.powerStation = powerStation;
    this.isPowered = isPowered;

  }

  // draws the power source
  WorldImage drawPowerSource() {
    return new OverlayImage(new CircleImage(6, OutlineMode.SOLID, Color.MAGENTA), 
        new StarImage(20, OutlineMode.SOLID, Color.BLUE));
  }

  //draws a wire
  WorldImage drawWire(int x, int y) {
    return new RectangleImage(x, y, OutlineMode.SOLID, this.color);
  }

  // draws this game piece
  WorldImage draw(LightEmAll game) {
    WorldImage background = new RectangleImage(this.size, this.size, 
        OutlineMode.SOLID, Color.DARK_GRAY);

    WorldImage border = new RectangleImage(this.size, this.size, 
        OutlineMode.OUTLINE, Color.BLACK);

    // thickness of wire one twenty-second the size of game piece, length one half
    if (this.top) {      
      background = new OverlayImage(this.drawWire(this.size / 22, 
          this.size / 2).movePinholeTo(new Posn(0, this.size / 4)), 
          background);         
    }

    if (this.bottom) {
      background = new OverlayImage(this.drawWire(this.size / 22, 
          this.size / 2).movePinholeTo(new Posn(0, (-1) * (this.size / 4))), 
          background);
    }

    if (this.left) {
      background = new OverlayImage(this.drawWire(this.size / 2, 
          this.size / 22).movePinholeTo(new Posn((this.size / 4), 0)), 
          background);
    }

    if (this.right) {
      background = new OverlayImage(this.drawWire(this.size / 2, 
          this.size / 22).movePinholeTo(new Posn((-1) * (this.size / 4), 0)), 
          background);
    }

    // only one game piece should have the power station
    if (this.powerStation) {
      background = new OverlayImage(this.drawPowerSource(), background);
    }

    return new OverlayImage(border, background);
  }

  // set color of GamePiece wiring based on powered and power fields
  public Color changeColor(int radius) {
    if (!this.isPowered || this.distance <= 0) {
      this.color = Color.GRAY;
    }
    else {
      this.color = new Color(255 - ((radius - this.distance) * 50 / radius),
          255 - ((radius - this.distance) * 200 / radius), 100);
    }
    return this.color;
  }

  // checks if two game pieces are the same based on their coordinates
  public boolean sameNode(GamePiece g) {
    return this.row == g.row && this.col == g.col;
  }
}

class Edge {
  GamePiece fromNode;
  GamePiece toNode;
  int weight;

  Edge(GamePiece fromNode, GamePiece toNode) {
    this.fromNode = fromNode;
    this.toNode = toNode;
  }
}


class LightEmAll extends World {
  // a list of columns of GamePieces,
  // i.e., represents the board in column-major order
  ArrayList<ArrayList<GamePiece>> board;
  // a list of all nodes
  ArrayList<GamePiece> nodes;
  // a list of edges of the minimum spanning tree
  ArrayList<Edge> mst;
  // the width and height of the board
  int width;
  int height;
  Integer time;
  Integer moves;
  // the current location of the power station,
  // as well as its effective radius
  int powerRow;
  int powerCol;
  int radius;
  HashMap<GamePiece, GamePiece> representatives;
  ArrayList<Edge> edgesInGraph;


  LightEmAll(int width, int height) {
    this.width = width;
    this.height = height;
    this.radius = this.width;
    this.time = 0;
    this.moves = 0;
    this.board = new ArrayList<ArrayList<GamePiece>>();
    this.nodes = new ArrayList<GamePiece>();
    this.mst = new ArrayList<Edge>();
    this.representatives = new HashMap<GamePiece, GamePiece>();
    this.edgesInGraph = new ArrayList<Edge>();
    this.createBoard();
    this.createNodeList();
    this.getAllEdges();
    this.initializeReps();
    this.kruskalMST();
    this.changeWires();
    this.initNeighbors();
    this.setDefaults();
  }


  //initializes the list of neighbors for each game piece
  public void initNeighbors() {

    for (ArrayList<GamePiece> col: board) {

      for (GamePiece g : col) {
        g.neighbors = new ArrayList<GamePiece>();

        if (g.left && g.col > 0) {
          GamePiece n = this.board.get(g.col - 1).get(g.row); 
          g.neighbors.add(n);
        }

        if (g.right && g.col < this.width - 1) {
          GamePiece n = this.board.get(g.col + 1).get(g.row); 
          g.neighbors.add(n);
        }

        if (g.top && g.row > 0) {
          GamePiece n = this.board.get(g.col).get(g.row - 1);
          g.neighbors.add(n);
        }

        if (g.bottom && g.row < this.height - 1) {
          GamePiece n = this.board.get(g.col).get(g.row + 1); 
          g.neighbors.add(n);
        }
      }
    }
  }


  // draws the game board
  public WorldScene makeScene() {
    WorldScene scene = this.getEmptyScene();
    WorldImage game = new EmptyImage();
    WorldImage movesInfo = 
        new OverlayImage(new TextImage("Moves: " + this.moves.toString(), Color.BLACK), 
            new RectangleImage(this.width * 40, 40, OutlineMode.SOLID, 
                Color.DARK_GRAY).movePinholeTo(new Posn((-10) * this.width, 
                    0))).movePinholeTo(new Posn(10 * this.width, 0));

    WorldImage info = new OverlayImage(new TextImage("Time: " + this.time.toString(), 
        Color.BLACK), movesInfo).movePinholeTo(new Posn(0, 0));
    WorldImage button = new CircleImage(15, OutlineMode.SOLID, Color.RED);
    WorldImage startOver = new OverlayImage(new TextImage("end", Color.BLACK), button);
    WorldImage topBar = new OverlayImage(startOver, info);

    for (ArrayList<GamePiece> col : this.board) {

      WorldImage line = new EmptyImage();
      for (GamePiece g: col) {
        line = new AboveImage(line, g.draw(this));
      }

      game = new BesideAlignImage(AlignModeY.MIDDLE, game, line);

    }
    game = new AboveImage(topBar, game);
    game.movePinholeTo(new Posn(this.width * 20, this.height * 20 + 20));
    scene.placeImageXY(game, this.width * 20, this.height * 20 + 20);
    return scene;
  }


  // creates an ArrayList of an ArrayList of game pieces
  public void createBoard() {
    for (int j = 0; j < this.width; j++) {
      ArrayList<GamePiece> col = new ArrayList<GamePiece>();
      if (j == 0) {
        col = this.makeCol(j, false, false, false, false, true);
      }
      else {
        col = this.makeCol(j, false, false, false, false, false);
      }
      this.board.add(col);
    }
  }


  // makes a column of game pieces
  public ArrayList<GamePiece> makeCol(int j, boolean left, boolean right, 
      boolean top, boolean bottom, boolean powerStation) {
    ArrayList<GamePiece> col = new ArrayList<GamePiece>();
    for (int i = 0; i < this.height; i++) {
      if (i == 0 && powerStation) {
        col.add(new GamePiece(i, j, left, right, top, bottom, true, false));
        this.powerCol = 0;
        this.powerRow = 0;
      }
      else {
        col.add(new GamePiece(i, j, left, right, top, bottom, false, false));
      }
    }
    return col;
  }

  // finds all of the valid edges in the graph
  public void getAllEdges() {
    for (int j = 0; j < this.width; j++) {
      for (int i = 0; i < this.height; i++) {


        if ((j == 0 && i == 0) || (j == 0 && i != 0 && i != this.height - 1)
            || (j != 0 && j != this.width - 1 && i == 0) 
            || (j != 0 && j != this.width - 1 && i != 0 && i != this.height - 1)) {
          Edge e1 = new Edge(this.board.get(j).get(i), this.board.get(j).get(i + 1));
          Edge e2 = new Edge(this.board.get(j).get(i), this.board.get(j + 1).get(i));

          this.edgesInGraph.add(e1);
          this.edgesInGraph.add(e2);
        }

        if ((j == 0 && i == this.height - 1) 
            || (j != 0 && j != this.width - 1 && i == this.height - 1)) {
          Edge e1 = new Edge(this.board.get(j).get(i), this.board.get(j + 1).get(i));

          this.edgesInGraph.add(e1);

        }

        if ((j == this.width - 1 && i == 0) 
            || j == this.width - 1 && i != 0 && i != this.height - 1) {
          Edge e1 = new Edge(this.board.get(j).get(i), this.board.get(j).get(i + 1));

          this.edgesInGraph.add(e1);
        }

      }
    }
    this.randomizeEdges();
  }

  // randomizes the weights for each edge
  public void randomizeEdges() {
    for (Edge e : edgesInGraph) {
      // preference for vertical wires: range of random numbers 
      //for weights is smaller for vertical than horizontal
      if (java.lang.Math.abs(e.fromNode.row - e.toNode.row) == 1) {
        e.weight = new Random().nextInt(40);
      }
      else {
        e.weight = new Random().nextInt(80);

      }
    }
  }

  // sorts the edges based on weight from smallest to largest
  public <T> void sortEdges(ICompare<T> comp) {
    // Create a temporary array
    ArrayList<Edge> temp = new ArrayList<Edge>();

    // Make sure the temporary array is exactly as big as the given array
    for (int i = 0; i < this.edgesInGraph.size(); i = i + 1) {
      temp.add(this.edgesInGraph.get(i));
    }
    sortEdgesHelp(edgesInGraph, temp, comp, 0, edgesInGraph.size());
  }

  // sorts both halves of list and then merges them
  public <T> void sortEdgesHelp(ArrayList<Edge> source, ArrayList<Edge> temp, ICompare<T> comp,
      int loIdx, int hiIdx) {
    // Step 0: stop when finished
    if (hiIdx - loIdx <= 1) {
      return; // nothing to sort
    }
    // Step 1: find the middle index
    int midIdx = (loIdx + hiIdx) / 2;
    // Step 2: recursively sort both halves
    sortEdgesHelp(source, temp, comp, loIdx, midIdx);
    sortEdgesHelp(source, temp, comp, midIdx, hiIdx);
    // Step 3: merge the two sorted halves
    mergeEdges(source, temp, comp, loIdx, midIdx, hiIdx);
  }

  // merges two sorted halves together
  public <T> void mergeEdges(ArrayList<Edge> source, ArrayList<Edge> temp, ICompare<T> comp, 
      int loIdx, int midIdx, int hiIdx) {
    int curLo = loIdx;   // where to start looking in the lower half-list
    int curHi = midIdx;  // where to start looking in the upper half-list
    int curCopy = loIdx; // where to start copying into the temp storage

    while (curLo < midIdx && curHi < hiIdx) {
      if (comp.apply(source.get(curLo).weight, source.get(curHi).weight)) {
        // the value at curLo is smaller, so it comes first
        temp.set(curCopy, source.get(curLo));
        curLo = curLo + 1; // advance the lower index
      }
      else {
        // the value at curHi is smaller, so it comes first
        temp.set(curCopy, source.get(curHi));
        curHi = curHi + 1; // advance the upper index
      }
      curCopy = curCopy + 1; // advance the copying index
    }

    // copy everything that's left -- at most one of the two half-lists still has items in it
    while (curLo < midIdx) {
      temp.set(curCopy, source.get(curLo));
      curLo = curLo + 1;
      curCopy = curCopy + 1;
    }

    while (curHi < hiIdx) {
      temp.set(curCopy, source.get(curHi));
      curHi = curHi + 1;
      curCopy = curCopy + 1;
    }

    // copy everything back from temp into source
    for (int i = loIdx; i < hiIdx; i = i + 1) {
      source.set(i, temp.get(i));
    }
  }

  // initializes each game pieces representative to itself
  public void initializeReps() {
    for (GamePiece g: this.nodes) {
      this.representatives.put(g, g);
    }
  }

  // finds the minimum spanning tree of a graph 
  // that minimizes edge weights via Kruskal's algorithm
  public void kruskalMST() {
    this.sortEdges(new CompareEdges());

    // minimum spanning tree has n - 1 edges for graph with n nodes
    while (this.mst.size() < this.nodes.size() - 1) {

      for (Edge e: this.edgesInGraph) {
        if (this.find(e.fromNode).sameNode(this.find(e.toNode))) {
          // does not add edge if it would create cycle

        }
        else {
          this.mst.add(e);
          this.union(e.fromNode, e.toNode);
        }
      }
    }
  }

  // returns the representative of a given game piece
  public GamePiece find(GamePiece g) {
    if (g.sameNode(this.representatives.get(g))) {
      return g;
    }
    else {
      return this.find(this.representatives.get(g));
    }
  }

  // changes the representative of g2 to the representative
  // of g1
  public void union(GamePiece g1, GamePiece g2) {
    this.representatives.put(g2, g1);
  }

  // changes the connection values of the game pieces
  // based on the edges in the minimum spanning tree
  public void changeWires() {

    for (Edge e: this.mst) {

      if (e.fromNode.row > e.toNode.row) {
        e.fromNode.top = true;
        e.toNode.bottom = true;
      }

      if (e.toNode.row > e.fromNode.row) {
        e.fromNode.bottom = true;
        e.toNode.top = true;
      }

      if (e.toNode.col > e.fromNode.col) {
        e.fromNode.right = true;
        e.toNode.left = true;
      }

      if (e.fromNode.col > e.toNode.col) {
        e.fromNode.left = true;
        e.toNode.right = true;
      }
    }
  }

  // adds all of the game pieces into an ArrayList of game pieces
  public void createNodeList() {
    for (ArrayList<GamePiece> col: this.board) {
      for (GamePiece g: col) {
        this.nodes.add(g);
      }
    }
  }

  // rotates the game piece clockwise with each click
  public void onMouseClicked(Posn mousePosn, String button) {
    if (button.equals("LeftButton") && mousePosn.y > 40) {

      this.moves = this.moves + 1;

      GamePiece temp = this.board.get(mousePosn.x / 40).get((mousePosn.y / 40) - 1);
      GamePiece holder = new GamePiece(temp.row, temp.col, temp.left, 
          temp.right, temp.top, temp.bottom, temp.powerStation, temp.isPowered);

      if (temp.top) {
        temp.right = true;
      }
      if (temp.right) {
        temp.bottom = true;
      }
      if (temp.bottom) {
        temp.left = true;
      }
      if (temp.left) {
        temp.top = true;
      }

      if (!holder.top) {
        temp.right = false;
      }      
      if (!holder.right) {
        temp.bottom = false;
      }
      if (!holder.bottom) {
        temp.left = false;
      }
      if (!holder.left) {
        temp.top = false;
      }

      this.initNeighbors();

      this.setDefaults();
    }

    // starts game over when red button is clicked
    if (button.equals("LeftButton") && (5 < mousePosn.y) && (mousePosn.y < 35)
        && (((this.width * 20) - 15) < mousePosn.x) && (mousePosn.x < ((this.width * 20) + 15))) {
      this.board = new ArrayList<ArrayList<GamePiece>>();
      this.createBoard();
      this.createNodeList();
      this.getAllEdges();
      this.initializeReps();
      this.kruskalMST();
      this.changeWires();
      this.initNeighbors();
      this.moves = 0;
      this.time = 0;
    }

    this.setDefaults();

  }

  // Set default values for non-power station nodes for color and power
  // and update based on distance from power station
  public void setDefaults() {
    
    for (GamePiece piece : this.nodes) {
      piece.isPowered = this.hasPower(piece, new ArrayList<GamePiece>());
    }
    
    ArrayList<GamePiece> seen = new ArrayList<GamePiece>();
    ArrayList<GamePiece> workList = new ArrayList<GamePiece>();
    for (GamePiece p : this.nodes) {
      if (p.powerStation) {
        p.distance = this.radius;
        p.color = Color.YELLOW;
        workList.add(p);
      }
      else {
        p.distance = 0;
        p.color = Color.GRAY;
      }
    }
    while (workList.size() != 0) {
      GamePiece p = workList.remove(0);
      if (!seen.contains(p)) {
        seen.add(p);
        ArrayList<GamePiece> neighbors = this.listAllNeighbors(p);
        for (GamePiece neighbor : neighbors) {
          if (!seen.contains(neighbor)) {
            neighbor.distance = p.distance - 1;
            neighbor.changeColor(this.radius);
            workList.add(neighbor);
          }
        }
      }
    }
  }

  // return list of surrounding GamePieces mutually connected with given GamePiece
  public ArrayList<GamePiece> listAllNeighbors(GamePiece piece) {
    ArrayList<GamePiece> neighbors = new ArrayList<GamePiece>();

    if (this.connectLeft(piece)) {
      neighbors.add(this.board.get(piece.col - 1).get(piece.row));
    }

    if (this.connectRight(piece)) {
      neighbors.add(this.board.get(piece.col + 1).get(piece.row));
    }

    if (this.connectAbove(piece)) {
      neighbors.add(this.board.get(piece.col).get(piece.row - 1));
    }

    if (this.connectBelow(piece)) {
      neighbors.add(this.board.get(piece.col).get(piece.row + 1));
    }

    return neighbors;
  }

  // moves the power station based on the keys pressed if there is a valid wire connection
  public void onKeyEvent(String key) {
    GamePiece current = this.board.get(this.powerCol).get(this.powerRow);

    boolean left = key.equals("left") && this.powerCol > 0 && current.left 
        && this.board.get(this.powerCol - 1).get(this.powerRow).right;
    boolean right = key.equals("right") && this.powerCol < (this.width - 1) && current.right
        && this.board.get(this.powerCol + 1).get(this.powerRow).left;
    boolean up = key.equals("up") && this.powerRow > 0 && current.top 
        && this.board.get(this.powerCol).get(this.powerRow - 1).bottom;
    boolean down = key.equals("down") && this.powerRow < (this.height - 1) && current.bottom
        && this.board.get(this.powerCol).get(this.powerRow + 1).top;

    if (left) {
      current.powerStation = false;
      this.board.get(this.powerCol - 1).get(this.powerRow).powerStation = true;
      this.powerCol = this.powerCol - 1;
      this.setDefaults();
    }

    if (right) {
      current.powerStation = false;
      this.board.get(this.powerCol + 1).get(this.powerRow).powerStation = true;
      this.powerCol = this.powerCol + 1;
      this.setDefaults();
    }

    if (up) {
      current.powerStation = false;
      this.board.get(this.powerCol).get(this.powerRow - 1).powerStation = true;
      this.powerRow = this.powerRow - 1;
      this.setDefaults();
    }

    if (down) {
      current.powerStation = false;
      this.board.get(this.powerCol).get(this.powerRow + 1).powerStation = true;
      this.powerRow = this.powerRow + 1;
      this.setDefaults();
    }
  }


  // increases the second count by one with each tick
  public void onTick() {
    this.time = this.time + 1;
  }


  // is there a path from the power station to the given GamePiece
  public boolean hasPower(GamePiece piece, ArrayList<GamePiece> seen) {
    if (!seen.contains(piece)) {
      seen.add(piece);
      return (this.connectLeft(piece) 
          && this.hasPower(this.board.get(piece.col - 1).get(piece.row), seen))
          || (this.connectRight(piece) 
              && this.hasPower(this.board.get(piece.col + 1).get(piece.row), seen))
          || (this.connectAbove(piece) 
              && this.hasPower(this.board.get(piece.col).get(piece.row - 1), seen))
          || (this.connectBelow(piece) 
              && this.hasPower(this.board.get(piece.col).get(piece.row + 1), seen));
    }
    return piece.powerStation || seen.contains(this.board.get(this.powerCol).get(this.powerRow));
  }

  // is this GamePiece connected to the GamePiece to its left
  public boolean connectLeft(GamePiece p) {
    return p.left && p.col != 0 && this.board.get(p.col - 1).get(p.row).right;
  }

  // is this GamePiece connected to the GamePiece to its right
  public boolean connectRight(GamePiece p) {
    return p.right && p.col != this.width - 1
        && this.board.get(p.col + 1).get(p.row).left;
  }

  // is this GamePiece connected to the GamePiece above
  public boolean connectAbove(GamePiece p) {
    return p.top && p.row != 0 && this.board.get(p.col).get(p.row - 1).bottom;
  }

  // is this GamePiece connected to the GamePiece below
  public boolean connectBelow(GamePiece p) {
    return p.bottom && p.row != this.height - 1
        && this.board.get(p.col).get(p.row + 1).top;
  }
}

//represents comparison between two objects of type T
interface ICompare<T> {
  boolean apply(int weight, int weight2);
}

//compares two Instructor objects
class CompareEdges implements ICompare<Edge> {
  public boolean apply(int weight1, int weight2) {
    return (weight1 <= weight2);
  }
}

class ExamplesLightGame {

  LightEmAll game;

  GamePiece g1;
  GamePiece g2;
  GamePiece g3;
  GamePiece g4;
  GamePiece g5;

  GamePiece g6;
  GamePiece g7;
  GamePiece g8;
  GamePiece g9;
  GamePiece g10;

  GamePiece g11;
  GamePiece g12;
  GamePiece g13;
  GamePiece g14;
  GamePiece g15;

  GamePiece g16;
  GamePiece g17;
  GamePiece g18;
  GamePiece g19;
  GamePiece g20;

  GamePiece g21;
  GamePiece g22;
  GamePiece g23;
  GamePiece g24;
  GamePiece g25;

  GamePiece g26;
  GamePiece g27;
  GamePiece g28;
  GamePiece g29;
  GamePiece g30;

  WorldImage background;

  ArrayList<GamePiece> col1;
  ArrayList<GamePiece> col2;
  ArrayList<GamePiece> col3;
  ArrayList<GamePiece> col4;
  ArrayList<GamePiece> col5;

  ArrayList<ArrayList<GamePiece>> gameBoard;

  // test BigBang for game
  void testBigBang(Tester t) {
    LightEmAll startTest = new LightEmAll(8, 8);
    int worldWidth = 40 * startTest.width;
    int worldHeight = 40 * startTest.height + 40;
    double tickRate = 1.0;
    startTest.bigBang(worldWidth, worldHeight, tickRate);
  }

  // initialize data
  void init() {

    game = new LightEmAll(5, 5);

    g1 = new GamePiece(0, 0, false, false, false, true, true, true);
    g2 = new GamePiece(1, 0, false, true, true, true, false, true);
    g3 = new GamePiece(2, 0, false, true, true, true, false, true);
    g4 = new GamePiece(3, 0, false, false, true, true, false, true);
    g5 = new GamePiece(4, 0, false, true, true, false, false, true);

    g6 = new GamePiece(0, 1, false, false, false, true, true, true);
    g7 = new GamePiece(1, 1, true, false, true, false, false, true);
    g8 = new GamePiece(2, 1, true, true, false, false, false, true);
    g9 = new GamePiece(3, 1, false, false, false, true, false, true); 
    g10 = new GamePiece(4, 1, true, true, true, false, false, true);

    g11 = new GamePiece(0, 2, false, false, false, true, false, true);
    g12 = new GamePiece(1, 2, false, false, true, true, false, true);
    g13 = new GamePiece(2, 2, true, false, true, false, false, true); 
    g14 = new GamePiece(3, 2, false, false, false, true, false, true);
    g15 = new GamePiece(4, 2, true, true, true, false, false, true);

    g16 = new GamePiece(0, 3, false, false, false, true, false, true);
    g17 = new GamePiece(1, 3, false, false, true, true, false, true); 
    g18 = new GamePiece(2, 3, false, true, true, false, false, true);
    g19 = new GamePiece(3, 3, false, false, false, true, false, true); 
    g20 = new GamePiece(4, 3, true, true, true, false, false, true);

    g21 = new GamePiece(0, 4, false, false, false, true, false, true);
    g22 = new GamePiece(1, 4, false, false, true, true, false, true);
    g23 = new GamePiece(2, 4, true, false, true, true, false, true); 
    g24 = new GamePiece(3, 4, false, false, true, true, false, true);
    g25 = new GamePiece(4, 4, true, false, true, false, false, true);

    g26 = new GamePiece(0, 0, false, false, false, true, false, true);
    g27 = new GamePiece(4, 0, false, true, true, false, false, true);
    g28 = new GamePiece(0, 4, false, false, false, true, false, true);
    g29 = new GamePiece(4, 4, true, false, true, false, false, true);
    g30 = new GamePiece(3, 2, false, false, false, false, false, true);


    col1 = new ArrayList<GamePiece>(Arrays.asList(this.g1, this.g2, 
        this.g3, this.g4, this.g5));
    col2 = new ArrayList<GamePiece>(Arrays.asList(this.g6, this.g7, 
        this.g8, this.g9, this.g10));
    col3 = new ArrayList<GamePiece>(Arrays.asList(this.g11, this.g12, 
        this.g13, this.g14, this.g15));
    col4 = new ArrayList<GamePiece>(Arrays.asList(this.g16, this.g17, 
        this.g18, this.g19, this.g20));
    col5 = new ArrayList<GamePiece>(Arrays.asList(this.g21, this.g22, 
        this.g23, this.g24, this.g25));

    game.board = new ArrayList<ArrayList<GamePiece>>(Arrays.asList(this.col1, 
        this.col2, this.col2, this.col4, this.col5));

    background = new RectangleImage(40, 40, OutlineMode.SOLID, Color.DARK_GRAY);

  }
  
  // initialize data
  void init2() {

    game = new LightEmAll(5, 5);

    g1 = new GamePiece(0, 0, false, false, false, false, true, false);
    g2 = new GamePiece(1, 0, false, false, false, false, false, false);
    g3 = new GamePiece(2, 0, false, false, false, false, false, false);
    g4 = new GamePiece(3, 0, false, false, false, false, false, false);
    g5 = new GamePiece(4, 0, false, false, false, false, false, false);

    g6 = new GamePiece(0, 1, false, false, false, false, false, false);
    g7 = new GamePiece(1, 1, false, false, false, false, false, false);
    g8 = new GamePiece(2, 1, false, false, false, false, false, false);
    g9 = new GamePiece(3, 1, false, false, false, false, false, false); 
    g10 = new GamePiece(4, 1, false, false, false, false, false, false);

    g11 = new GamePiece(0, 2, false, false, false, false, false, false);
    g12 = new GamePiece(1, 2, false, false, false, false, false, false);
    g13 = new GamePiece(2, 2, false, false, false, false, false, false); 
    g14 = new GamePiece(3, 2, false, false, false, false, false, false);
    g15 = new GamePiece(4, 2, false, false, false, false, false, false);

    g16 = new GamePiece(0, 3, false, false, false, false, false, false);
    g17 = new GamePiece(1, 3, false, false, false, false, false, false); 
    g18 = new GamePiece(2, 3, false, false, false, false, false, false);
    g19 = new GamePiece(3, 3, false, false, false, false, false, false); 
    g20 = new GamePiece(4, 3, false, false, false, false, false, false);

    g21 = new GamePiece(0, 4, false, false, false, false, false, false);
    g22 = new GamePiece(1, 4, false, false, false, false, false, false);
    g23 = new GamePiece(2, 4, false, false, false, false, false, false); 
    g24 = new GamePiece(3, 4, false, false, false, false, false, false);
    g25 = new GamePiece(4, 4, false, false, false, false, false, false);

    g26 = new GamePiece(0, 0, false, false, false, false, false, false);
    g27 = new GamePiece(4, 0, false, false, false, false, false, false);
    g28 = new GamePiece(0, 4, false, false, false, false, false, false);
    g29 = new GamePiece(4, 4, false, false, false, false, false, false);
    g30 = new GamePiece(3, 2, false, false, false, false, false, false);


    col1 = new ArrayList<GamePiece>(Arrays.asList(this.g1, this.g2, 
        this.g3, this.g4, this.g5));
    col2 = new ArrayList<GamePiece>(Arrays.asList(this.g6, this.g7, 
        this.g8, this.g9, this.g10));
    col3 = new ArrayList<GamePiece>(Arrays.asList(this.g11, this.g12, 
        this.g13, this.g14, this.g15));
    col4 = new ArrayList<GamePiece>(Arrays.asList(this.g16, this.g17, 
        this.g18, this.g19, this.g20));
    col5 = new ArrayList<GamePiece>(Arrays.asList(this.g21, this.g22, 
        this.g23, this.g24, this.g25));

    game.board = new ArrayList<ArrayList<GamePiece>>(Arrays.asList(this.col1, 
        this.col2, this.col2, this.col4, this.col5));

    background = new RectangleImage(40, 40, OutlineMode.SOLID, Color.DARK_GRAY);

  }


  void testInitNeighbors(Tester t) {
    this.init();

    //game.initNeighbors();
    t.checkExpect(this.g1.neighbors, new ArrayList<GamePiece>(Arrays.asList(this.g6, this.g2)));
    t.checkExpect(this.g18.neighbors, 
        new ArrayList<GamePiece>(Arrays.asList(this.g13, this.g23, this.g17, this.g19)));
  }


  // test draw function
  void testDraw(Tester t) {
    this.init();

    WorldImage left = new RectangleImage(this.g1.size / 2, this.g1.size / 22, 
        OutlineMode.SOLID, Color.LIGHT_GRAY).movePinholeTo(new Posn((g1.size / 4), 0));

    WorldImage right = new RectangleImage(g1.size / 2, g1.size / 22, 
        OutlineMode.SOLID, Color.LIGHT_GRAY).movePinholeTo(new Posn((-1) * (g1.size / 4), 0));

    WorldImage top = new RectangleImage(g1.size / 22, g1.size / 2, 
        OutlineMode.SOLID, Color.LIGHT_GRAY).movePinholeTo(new Posn(0, g1.size / 4));

    WorldImage bottom = new RectangleImage(g1.size / 22, g1.size / 2, 
        OutlineMode.SOLID, Color.LIGHT_GRAY).movePinholeTo(new Posn(0, (-1) * (g1.size / 4)));

    WorldImage border = new RectangleImage(this.g1.size, 
        this.g1.size, OutlineMode.OUTLINE, Color.BLACK);


    WorldImage borderlessPieceLeft = 
        new OverlayImage(left, background).movePinholeTo(new Posn(0, 0));
    t.checkExpect(this.g1.draw(game), new OverlayImage(border, borderlessPieceLeft));

    GamePiece g26 = new GamePiece(0, 0, false, true, false, false, false, true);
    WorldImage borderlessPieceRight = 
        new OverlayImage(right, background).movePinholeTo(new Posn(0, 0));
    t.checkExpect(g26.draw(game), new OverlayImage(border, borderlessPieceRight));

    GamePiece g27 = new GamePiece(0, 0, false, false, true, false, false, true);
    WorldImage borderlessPieceTop = 
        new OverlayImage(top, background).movePinholeTo(new Posn(0, 0));
    t.checkExpect(g27.draw(game), new OverlayImage(border, borderlessPieceTop));

    GamePiece g28 = new GamePiece(0, 0, false, false, false, true, false, true);
    WorldImage borderlessPieceBottom = 
        new OverlayImage(bottom, background).movePinholeTo(new Posn(0, 0));
    t.checkExpect(g28.draw(game), new OverlayImage(border, borderlessPieceBottom));

    WorldImage twoWires = new OverlayImage(bottom, 
        borderlessPieceTop).movePinholeTo(new Posn(0, 0));
    WorldImage twoWiresBorder = new OverlayImage(border, twoWires);
    t.checkExpect(this.g6.draw(game), twoWiresBorder);

    GamePiece g29 = new GamePiece(0, 0, true, false, true, true, false, true);
    WorldImage threeWires = new OverlayImage(left, 
        twoWires).movePinholeTo(new Posn(0, 0));
    WorldImage threeWiresBorder = new OverlayImage(border, threeWires);
    t.checkExpect(g29.draw(game), threeWiresBorder);

    WorldImage fourWires = new OverlayImage(right, 
        threeWires).movePinholeTo(new Posn(0, 0));
    WorldImage fourWiresBorder = new OverlayImage(border, fourWires);
    t.checkExpect(this.g14.draw(game), fourWiresBorder);

    WorldImage powerStation = new OverlayImage(new CircleImage(6, 
        OutlineMode.SOLID, Color.MAGENTA) , 
        new StarImage(20, OutlineMode.SOLID, Color.BLUE));
    WorldImage powerCell = new OverlayImage(powerStation, 
        fourWires).movePinholeTo(new Posn(0, 0));
    WorldImage powerCellBorder = new OverlayImage(border, powerCell);  
    t.checkExpect(this.g13.draw(game), powerCellBorder);

  }

  
  // test makeScene function
  void testMakeScene(Tester t) {
    this.init();

    WorldImage drawMovesInfo = 
        new OverlayImage(new TextImage("Moves: " + game.moves.toString(), Color.BLACK), 
            new RectangleImage(game.width * 40, 40, OutlineMode.SOLID, 
                Color.DARK_GRAY).movePinholeTo(new Posn((-10) * game.width, 
                    0))).movePinholeTo(new Posn(10 * game.width, 0));

    WorldImage drawInfo = new OverlayImage(new TextImage("Time: " + game.time.toString(), 
        Color.BLACK), drawMovesInfo).movePinholeTo(new Posn(0, 0));
    WorldImage drawButton = new CircleImage(15, OutlineMode.SOLID, Color.RED);
    WorldImage drawStartOver = new OverlayImage(
        new TextImage("end", Color.BLACK), drawButton);
    WorldImage drawTopBar = new OverlayImage(drawStartOver, drawInfo);

    WorldImage drawCol1 = new AboveImage(this.g1.draw(this.game), 
        new AboveImage(this.g2.draw(this.game), 
            new AboveImage(this.g3.draw(this.game), 
                new AboveImage(this.g4.draw(this.game), 
                    new AboveImage(this.g5.draw(this.game), new EmptyImage())))));

    WorldImage drawCol2 = new AboveImage(this.g6.draw(this.game), 
        new AboveImage(this.g7.draw(this.game), 
            new AboveImage(this.g8.draw(this.game), 
                new AboveImage(this.g9.draw(this.game), 
                    new AboveImage(this.g10.draw(this.game), new EmptyImage())))));

    WorldImage drawCol3 = new AboveImage(this.g11.draw(this.game), 
        new AboveImage(this.g12.draw(this.game), 
            new AboveImage(this.g13.draw(this.game), 
                new AboveImage(this.g14.draw(this.game), 
                    new AboveImage(this.g15.draw(this.game), new EmptyImage())))));

    WorldImage drawCol4 = new AboveImage(this.g16.draw(this.game), 
        new AboveImage(this.g17.draw(this.game), 
            new AboveImage(this.g18.draw(this.game), 
                new AboveImage(this.g19.draw(this.game), 
                    new AboveImage(this.g20.draw(this.game), new EmptyImage())))));

    WorldImage drawCol5 = new AboveImage(this.g21.draw(this.game), 
        new AboveImage(this.g22.draw(this.game), 
            new AboveImage(this.g23.draw(this.game), 
                new AboveImage(this.g24.draw(this.game), 
                    new AboveImage(this.g25.draw(this.game), new EmptyImage())))));

    WorldImage drawBoard = new BesideAlignImage(AlignModeY.MIDDLE, drawCol1,
        new BesideAlignImage(AlignModeY.MIDDLE, drawCol2, 
            new BesideAlignImage(AlignModeY.MIDDLE, drawCol3, 
                new BesideAlignImage(AlignModeY.MIDDLE, drawCol4, 
                    new BesideAlignImage(AlignModeY.MIDDLE, drawCol5,
                        new EmptyImage())))));

    WorldImage drawGame = new AboveImage(drawTopBar, drawBoard);

    t.checkExpect(game.makeScene(), drawGame);
  }
   

  // test drawPowerSource function
  void testDrawPowerSource(Tester t) {
    this.init();

    t.checkExpect(this.g5.drawPowerSource(), 
        new OverlayImage(new CircleImage(6, OutlineMode.SOLID, Color.MAGENTA) , 
            new StarImage(20, OutlineMode.SOLID, Color.BLUE)));

  }

  // test drawWire function
  void testDrawWire(Tester t) {
    this.init();

    t.checkExpect(this.g1.drawWire(g1.size / 2, g1.size / 22),
        new RectangleImage(g1.size / 2, g1.size / 22, OutlineMode.SOLID, Color.LIGHT_GRAY));
    t.checkExpect(this.g11.drawWire(g1.size / 22, g1.size / 2), 
        new RectangleImage(g1.size / 22, g1.size / 2, OutlineMode.SOLID, Color.LIGHT_GRAY));

  }

  // test createBoard function
  void testCreateBoard(Tester t) {
    this.init2();

    this.game.createBoard();

    t.checkExpect(game.board.get(0).get(0), this.g1);
    t.checkExpect(game.board.get(1).get(0), this.g6);
    t.checkExpect(game.board.get(2).get(3), this.g9);
    t.checkExpect(game.board.get(3).get(3), this.g19);
    t.checkExpect(game.board.get(4).get(1), this.g22);

  }

  // test makeCol function
  void testMakeCol(Tester t) {
    this.init2();

    t.checkExpect(this.game.makeCol(0, false, false, false, false, true), this.col1);
    t.checkExpect(this.game.makeCol(1, false, false, false, false, false), this.col2);
    t.checkExpect(this.game.makeCol(3, false, false, false, false, false), this.col4);
    t.checkExpect(this.game.makeCol(4, false, false, false, false, false), this.col5);

  }


  // test onMouseClicked function
  void testOnMouseClicked(Tester t) {
    this.init();

    this.game.onMouseClicked(new Posn(0, 40), "LeftButton");
    t.checkExpect(this.g1.left, true);
    t.checkExpect(this.g1.right, false);
    t.checkExpect(this.g1.top, false);
    t.checkExpect(this.g1.bottom, false);

    this.game.onMouseClicked(new Posn(2 * 40, (1 * 40) + 40), "LeftButton");
    t.checkExpect(this.g8.left, false);
    t.checkExpect(this.g8.right, false);
    t.checkExpect(this.g8.top, true);
    t.checkExpect(this.g8.bottom, true);

    this.game.onMouseClicked(new Posn(0, (2 * 40) + 40), "LeftButton");
    t.checkExpect(this.g11.left, true);
    t.checkExpect(this.g11.right, false);
    t.checkExpect(this.g11.top, false);
    t.checkExpect(this.g11.bottom, false);

    this.game.onMouseClicked(new Posn(2 * 40, (2 * 40) + 40), "LeftButton");
    t.checkExpect(this.g13.left, false);
    t.checkExpect(this.g13.right, true);
    t.checkExpect(this.g13.top, true);
    t.checkExpect(this.g13.bottom, false);

    this.game.onMouseClicked(new Posn(5 * 20, 20), "LeftButton");
    t.checkExpect(this.game.time, 0);
    t.checkExpect(this.game.moves, 0);
    t.checkExpect(this.g1.left, true);
    t.checkExpect(this.g8.left, false);
    t.checkExpect(this.g11.right, true);
  }


  void testOnKeyEvent(Tester t) {
    this.init();

    this.game.onKeyEvent("Left");
    t.checkExpect(this.g1.powerStation, true);

    this.game.onKeyEvent("Down");
    t.checkExpect(this.g1.powerStation, false);
    t.checkExpect(this.g2.powerStation, true);

    this.game.onKeyEvent("Right");
    t.checkExpect(this.g2.powerStation, false);
    t.checkExpect(this.g7.powerStation, true);
    this.game.onKeyEvent("Up");

    t.checkExpect(this.g7.powerStation, false);
    t.checkExpect(this.g6.powerStation, true);
  }

  void testOnTick(Tester t) {
    this.init();

    this.game.onTick();
    t.checkExpect(this.game.time, 1);
    this.game.onTick();
    t.checkExpect(this.game.time, 2);

  }

  void testKruskalMST(Tester t) {
    this.init();

    this.game.kruskalMST();
    t.checkExpect(game.mst.size(), 24);
  }

  void testSameNode(Tester t) {
    this.init();
    t.checkExpect(this.g1.sameNode(g2), false);
    t.checkExpect(this.g1.sameNode(g1), true);
  }

  void testFind(Tester t) {
    this.init();
    this.game.initializeReps();
    t.checkExpect(this.game.find(g1), g1);

  }

  void testUnion(Tester t) {
    this.init();

    this.game.initializeReps();
    t.checkExpect(this.game.representatives.get(g1), g1);
    this.game.union(this.g1, this.g2);
    t.checkExpect(this.game.representatives.get(g2), g1);
  }

  void testInitializeReps(Tester t) {
    this.init();

    this.game.initializeReps();
    t.checkExpect(this.game.representatives.get(g1), g1);
    t.checkExpect(this.game.representatives.get(g6), g6);

  }

  void testGetAllEdges(Tester t) {
    this.init();

    this.game.getAllEdges();
    t.checkExpect(this.game.edgesInGraph.size(), 40);
    t.checkExpect(this.game.edgesInGraph.get(0), new Edge(g1, g2));
    t.checkExpect(this.game.edgesInGraph.get(1), new Edge(g1, g6));
  }
  
  void testRandomizeEdges(Tester t) {
    this.init();
    
    this.game.getAllEdges();
    this.game.randomizeEdges();
    t.checkExpect(this.game.edgesInGraph.get(0).weight <= 40, true);
    t.checkExpect(this.game.edgesInGraph.get(1).weight <= 80, true);
    
  }
  
  void testHasPower(Tester t) {
    this.init();
    
    this.game.initializeReps();
    t.checkExpect(this.game.hasPower(g2, new ArrayList<GamePiece>()), true);
    t.checkExpect(this.game.hasPower(g30, new ArrayList<GamePiece>()), false);
  }
  
  void testConnectLeft(Tester t) {
    this.init();
    
    this.game.initializeReps();
    t.checkExpect(this.game.connectLeft(g1), false);
    t.checkExpect(this.game.connectLeft(g7), true);
  }
  
  void testConnectRight(Tester t) {
    this.init();
    
    this.game.initializeReps();
    t.checkExpect(this.game.connectRight(g1), false);
    t.checkExpect(this.game.connectRight(g7), false);
  }


}
