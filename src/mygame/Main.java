package mygame;

import com.jme3.app.SimpleApplication;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.Light;
import com.jme3.light.PointLight;
import com.jme3.light.SpotLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.shadow.DirectionalLightShadowFilter;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.texture.Texture;
import com.jme3.util.SkyFactory;
import com.jme3.util.TangentBinormalGenerator;

/**
 * test
 * @author James
 */
public class Main extends SimpleApplication {

    public static void main(String[] args) {
        Main app = new Main();
        app.start();
    }

    @Override
    public void simpleInitApp() {
     
        loadScene();
        setupLights();

        
        ShadowIntegratedLightingProcessor silp = new ShadowIntegratedLightingProcessor(assetManager);
        viewPort.addProcessor(silp);
        
        
        int test = 0; // [0..3]
        boolean useAmb = true;
       
        if (test == 0) {
            silp.addShadowCastingLight(directionalLight);
            rootNode.addLight(directionalLight);
            silp.addShadowCastingLight(directionalLight2);
            rootNode.addLight(directionalLight2);
        } else if (test == 1) {
            silp.addShadowCastingLight(pointLight);
            rootNode.addLight(pointLight);
            useAmb = false;
        } else if (test == 2) {
            silp.addShadowCastingLight(spotLight);
            rootNode.addLight(spotLight);
            useAmb = false;
        } else if (test == 3) {
            silp.addShadowCastingLight(spotLight);
            rootNode.addLight(spotLight);
            silp.addShadowCastingLight(pointLight);
            rootNode.addLight(pointLight);
            useAmb = false;
        }
        if (useAmb) rootNode.addLight(ambientLight);
        
        
        
        
//        DirectionalLightShadowRenderer dlsr = new DirectionalLightShadowRenderer(assetManager, 1024, 1);
//        dlsr.setLight(l);
//        dlsr.setLambda(0.55f);
//        dlsr.setShadowIntensity(0.6f);
//        dlsr.setEdgeFilteringMode(EdgeFilteringMode.PCFPOISSON);
//        dlsr.displayDebug();
//        viewPort.addProcessor(dlsr);
        
        
//        FilterPostProcessor fpp = new FilterPostProcessor(assetManager);
//        fpp.addFilter(dlsf);
//        viewPort.addProcessor(fpp);
        

//       ShadowTestUIManager uiMan = new ShadowTestUIManager(assetManager, dlsr, dlsf, guiNode, inputManager, viewPort);

        
        cam.setLocation(new Vector3f(65.25412f, 44.38738f, 9.087874f));
        cam.setRotation(new Quaternion(0.078139365f, 0.050241485f, -0.003942559f, 0.9956679f));

        flyCam.setMoveSpeed(100);
        
    }
    
    DirectionalLight directionalLight, directionalLight2;
    PointLight pointLight;
    SpotLight spotLight;
    AmbientLight ambientLight;
    
    private void setupLights() {
        
        directionalLight = new DirectionalLight();
        directionalLight.setColor(ColorRGBA.White.mult(0.5f));
        directionalLight.setDirection(new Vector3f(-1, -1, -1));
//        rootNode.addLight(directionalLight2);

        directionalLight2 = new DirectionalLight();
        directionalLight2.setColor(ColorRGBA.White.mult(0.5f));
        directionalLight2.setDirection(new Vector3f(-1, -1, 1));
//        rootNode.addLight(directionalLight2);
        

        pointLight = new PointLight();
        pointLight.setColor(ColorRGBA.Blue);
        pointLight.setPosition(new Vector3f(100, 100, 420));
        pointLight.setRadius(100000);
//        rootNode.addLight(pointLight);
        
        spotLight = new SpotLight();
        spotLight.setColor(ColorRGBA.Red.mult(1));
        spotLight.setPosition(new Vector3f(100, 101, 320));
        spotLight.setDirection(new Vector3f(-1,-1, 1));
        spotLight.setSpotInnerAngle(20*FastMath.DEG_TO_RAD);
        spotLight.setSpotOuterAngle(25*FastMath.DEG_TO_RAD);
        spotLight.setSpotRange(2000);
//        rootNode.addLight(spotLight);
        
        ambientLight = new AmbientLight();
        ambientLight.setColor(ColorRGBA.White.mult(0.1f));
       
    }
    
    
    private Spatial[] obj;
    private Material[] mat;
    
    public void loadScene() { 
        obj = new Spatial[2];
        // Setup first view


        mat = new Material[2];
        mat[0] = assetManager.loadMaterial("Common/Materials/RedColor.j3m");
        mat[1] = assetManager.loadMaterial("Textures/Terrain/Pond/Pond.j3m");
        
        mat[1].setBoolean("UseMaterialColors", true);
        mat[1].setColor("Ambient", ColorRGBA.White.mult(1.0f));
        mat[1].setColor("Diffuse", ColorRGBA.White.clone());
        

        obj[0] = new Geometry("sphere", new Sphere(30, 30, 2));
        obj[0].setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        obj[1] = new Geometry("cube", new Box(1.0f, 1.0f, 1.0f));
        obj[1].setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        TangentBinormalGenerator.generate(obj[1]);
        TangentBinormalGenerator.generate(obj[0]);


        for (int i = 0; i < 60; i++) {
            Spatial t = obj[FastMath.nextRandomInt(0, obj.length - 1)].clone(false);
            t.setLocalScale(FastMath.nextRandomFloat() * 10f);
            t.setMaterial(mat[FastMath.nextRandomInt(0, mat.length - 1)]);
            rootNode.attachChild(t);
            t.setLocalTranslation(FastMath.nextRandomFloat() * 200f, FastMath.nextRandomFloat() * 30f + 20, 30f * (i + 2f));
        }

        Box b = new Box(new Vector3f(0, 10, 350), 1000, 2, 1000);
        b.scaleTextureCoordinates(new Vector2f(10, 10));
        Spatial ground = new Geometry("soil", b);
        Material matGroundU = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        matGroundU.setColor("Color", ColorRGBA.Green);


        Material matGroundL = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        Texture grass = assetManager.loadTexture("Textures/Terrain/splat/grass.jpg");
        grass.setWrap(Texture.WrapMode.Repeat);
        matGroundL.setTexture("DiffuseMap", grass);
        matGroundL.setBoolean("UseMaterialColors", true);
        matGroundL.setColor("Ambient", ColorRGBA.White);
        matGroundL.setColor("Diffuse", ColorRGBA.White.clone());
        //matGroundL.setTexture("DiffuseMap", grass);
        
        
        ground.setMaterial(matGroundL);
        
        
        ground.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        rootNode.attachChild(ground);

        Spatial sky = SkyFactory.createSky(assetManager, "Scenes/Beach/FullskiesSunset0068.dds", false);
        sky.setLocalScale(350);

        rootNode.attachChild(sky);

    }

    
    
    @Override
    public void simpleUpdate(float tpf) {
        //TODO: add update code
    }

    @Override
    public void simpleRender(RenderManager rm) {
        //TODO: add render code
    }
}
