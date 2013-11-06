/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package mygame;

import com.jme3.asset.AssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.light.Light;
import com.jme3.light.PointLight;
import com.jme3.light.SpotLight;
import com.jme3.material.FixedFuncBinding;
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.material.MaterialDef;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Matrix4f;
import com.jme3.math.Vector4f;
import com.jme3.post.SceneProcessor;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.GeometryList;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.shader.VarType;
import com.jme3.shadow.AbstractShadowRenderer;
import com.jme3.shadow.CompareMode;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.shadow.PointLightShadowRenderer;
import com.jme3.shadow.SpotLightShadowRenderer;
import com.jme3.texture.FrameBuffer;
import java.util.*;

/**
 *
 * @author James
 * 
 * OK so this is very much a WIP at this stage, there is loads of features missing.
 * It is here simply for code methodoligy review, I need to figure out if its worth continuting down this path.
 * currently only supports 3 shadowing lights and will shit the bed if pointlights are used more than once or with
 * any other light type, since the limit of 16 textures is hit. I guess I need to pack all shadow maps into cubemaps.
 * It will probably break if HQ Lighting is used or vertex lighting or something, this is not tested yet, I know about it.
 * 
 */
public class ShadowIntegratedLightingProcessor implements SceneProcessor {

    static private int DEFAULT_MAP_SIZE = 1024;
    static private int DEFAULT_PSSM_SPLITS = 4;
    static private final String LIGHTING_MAT_ASSET_NAME = "Common/MatDefs/Light/Lighting.j3md"; 
    static private final String PATH_TO_UPDATED_LIGHTING = "MatDefs/LightNew/ShadowIntegratedLighting.j3md";
    static public final String LIGHT_COUNT_PREFIX = "LIGHTNUM_#_";
    
    
    protected List<Light> shadowCastingLights;
    protected List<AbstractShadowRenderer> shadowRenderers; 
    protected List<Material> shadowReceivingMaterials;
    protected Map<Light, AbstractShadowRenderer> lightToShadowRendererMap;
    
    protected AssetManager assetManager;
    protected ViewPort viewPort;
    protected RenderManager renderManager;

    private GeometryList sceneReceivers;
    private Vector4f tmpv = new Vector4f();
    
    public ShadowIntegratedLightingProcessor(AssetManager assetManager) { 
               
        this.assetManager = assetManager;
        
        shadowCastingLights = new ArrayList<Light>();
        shadowRenderers = new ArrayList<AbstractShadowRenderer>();
        shadowReceivingMaterials = new ArrayList<Material>();
        lightToShadowRendererMap = new HashMap<Light, AbstractShadowRenderer>();
      
    }
    
    public void registerAsShadowCaster(Light light) {
        addShadowCastingLight(light);
    }
    
    
    protected void addShadowCastingLight(Light light) {
        shadowCastingLights.add(light);
        
        // setup shadow renderer
        if (light instanceof DirectionalLight) {
            DirectionalLightShadowRenderer dlsr = new DirectionalLightShadowRenderer(this.assetManager, DEFAULT_MAP_SIZE, DEFAULT_PSSM_SPLITS);
            dlsr.setLight((DirectionalLight)light);
            
            addShadowRenderer(dlsr);
            lightToShadowRendererMap.put(light, dlsr);
            
        } else if (light instanceof PointLight) {
            PointLightShadowRenderer plsr = new PointLightShadowRenderer(this.assetManager, DEFAULT_MAP_SIZE);
            plsr.setLight((PointLight)light);
            
            addShadowRenderer(plsr);
            lightToShadowRendererMap.put(light, plsr);
            
        }  else if (light instanceof SpotLight) {
            SpotLightShadowRenderer slsr = new SpotLightShadowRenderer(this.assetManager, DEFAULT_MAP_SIZE);
            slsr.setLight((SpotLight)light);
            
            addShadowRenderer(slsr);
            lightToShadowRendererMap.put(light, slsr);
        }
        
    }
    
    private void addShadowRenderer(AbstractShadowRenderer shadowRenderer) {
        if (!shadowRenderers.isEmpty()) {
            shadowRenderers.get(shadowRenderers.size()-1).setFlushQueues(false); // make sure only the last added shadow renderer flushes the render queues
        }
        shadowRenderers.add(shadowRenderer);
        if (isInitialized()) {
            shadowRenderer.initialize(renderManager, viewPort);
        }
    }
    

    public void initialize(RenderManager rm, ViewPort vp) {
        this.renderManager = rm;
        this.viewPort = vp;
                
        for (AbstractShadowRenderer r : shadowRenderers) {
            r.initialize(rm, vp);
        }
    }



    public boolean isInitialized() {
        return viewPort != null;
    }

    
    public void preFrame(float tpf) {
        Matrix4f m = this.viewPort.getCamera().getViewProjectionMatrix();
        tmpv.set(m.m20, m.m21, m.m22, m.m23);
        
        for (Material mat : shadowReceivingMaterials) {
            mat.setVector4("ViewProjectionMatrixRow2", tmpv);
        }      
    }

    
    public void postQueue(RenderQueue rq) {
        
        if (shadowRenderers.isEmpty()) return; // @TODO: remove -- dev flag only
        
        sceneReceivers = rq.getShadowQueueContent(RenderQueue.ShadowMode.Receive);
         
         // run all the shadow renderers
         for (AbstractShadowRenderer r : shadowRenderers) {
             r.postQueue(rq);
             r.setPostShadowParams();
         }
        
        // make sure all materials that receive shadows are ready to accept the renderer information 
        // perhaps this doesnt need to be done every frame!
        processShadowReceivers();
        
        //update materials
                       
        for (Material mat : shadowReceivingMaterials) {
            updateMaterialDefinition(mat); // only needs to be called initially and when shadow lights are added or removed, it's here cause im lazy
            updateMaterialParams(mat);
        }
         
        
    }
   

    // take a material definition (.j3md) and add a set of shadow integrated params to it... basically saves defining all this in the j3md.
    private Material updateMaterialDefinition(Material mat) {
        MaterialDef def = mat.getMaterialDef();
        
        for (int ii=0; ii<shadowCastingLights.size(); ii++) {
            int lightCount = ii+1;
            Light l = shadowCastingLights.get(ii);
            String prefix = LIGHT_COUNT_PREFIX.replaceFirst("#", lightCount+"");
            
            // set a define for used lighting
            mat.getMaterialDef().getDefaultTechniques().get(0).addShaderPresetDefine(prefix+"USED", VarType.Boolean, true);
            
            
            // hacky information to figure out which light is being rendered
            // needs to match was is passed through in Material.renderMultipassLighting
            def.addMaterialParam(VarType.Vector4, prefix+"INFO_color", null, FixedFuncBinding.Color);
            def.addMaterialParam(VarType.Vector3, prefix+"INFO_direction", null, FixedFuncBinding.Color);
            def.addMaterialParam(VarType.Vector3, prefix+"INFO_position", null, FixedFuncBinding.Color);
                       
            def.addMaterialParam(VarType.Int, prefix+"FilterMode", 0, FixedFuncBinding.Color);
            def.addMaterialParam(VarType.Boolean, prefix+"HardwareShadows", null, FixedFuncBinding.Color);
            def.addMaterialParam(VarType.Vector4, prefix+"Splits", null, FixedFuncBinding.Color);
            def.addMaterialParam(VarType.Vector2, prefix+"FadeInfo", null, FixedFuncBinding.Color);
            def.addMaterialParam(VarType.Vector3, prefix+"LightPos", null, FixedFuncBinding.Color); //?
            def.addMaterialParam(VarType.Float, prefix+"PCFEdge", null, FixedFuncBinding.Color);
            def.addMaterialParam(VarType.Float, prefix+"ShadowMapSize", null, FixedFuncBinding.Color);     
            def.addMaterialParam(VarType.Float, prefix+"ShadowIntensity", null, FixedFuncBinding.Color); //unused legacy
            
            // not all these are needed, could be trimmed based on light type
            def.addMaterialParam(VarType.Texture2D, prefix+"ShadowMap0", null, FixedFuncBinding.Color); 
            def.addMaterialParam(VarType.Texture2D, prefix+"ShadowMap1", null, FixedFuncBinding.Color);
            def.addMaterialParam(VarType.Texture2D, prefix+"ShadowMap2", null, FixedFuncBinding.Color);
            def.addMaterialParam(VarType.Texture2D, prefix+"ShadowMap3", null, FixedFuncBinding.Color);
            def.addMaterialParam(VarType.Texture2D, prefix+"ShadowMap4", null, FixedFuncBinding.Color);
            def.addMaterialParam(VarType.Texture2D, prefix+"ShadowMap5", null, FixedFuncBinding.Color);

            
            def.addMaterialParam(VarType.Matrix4, prefix+"LightViewProjectionMatrix0", null, FixedFuncBinding.Color);
            def.addMaterialParam(VarType.Matrix4, prefix+"LightViewProjectionMatrix1", null, FixedFuncBinding.Color);
            def.addMaterialParam(VarType.Matrix4, prefix+"LightViewProjectionMatrix2", null, FixedFuncBinding.Color);
            def.addMaterialParam(VarType.Matrix4, prefix+"LightViewProjectionMatrix3", null, FixedFuncBinding.Color);
            def.addMaterialParam(VarType.Matrix4, prefix+"LightViewProjectionMatrix4", null, FixedFuncBinding.Color);
            def.addMaterialParam(VarType.Matrix4, prefix+"LightViewProjectionMatrix5", null, FixedFuncBinding.Color);      
        }
        
        return mat;
    }
    
     // for a given ShadowIntegratedLighting material, update all required shadow renderer information
     private Material updateMaterialParams(Material mat) {
        MaterialDef def = mat.getMaterialDef();
        
        for (int ii=0; ii<shadowCastingLights.size(); ii++) {
            
            Light l = shadowCastingLights.get(ii);
            String prefix = LIGHT_COUNT_PREFIX.replaceFirst("#", (ii+1)+"");

            // Asign the same values that the Material.renderMultipassLighting does to the global uniforms
            // NB: this is not 100% in sync with Material.renderMultipassLighting that's ok, it's enough to figure out which light we are using for now
            mat.setColor(prefix+"INFO_color", new ColorRGBA(l.getColor().getRed(), l.getColor().getGreen(), l.getColor().getBlue(), l.getType().getId()));
            if (l instanceof DirectionalLight) {
                mat.setVector3(prefix+"INFO_position", ((DirectionalLight)l).getDirection());
            } else if (l instanceof PointLight) {
                mat.setVector3(prefix+"INFO_position", ((PointLight)l).getPosition());
            } else if (l instanceof SpotLight) {
                mat.setVector3(prefix+"INFO_position", ((SpotLight)l).getPosition());
            }

            // copy over the shadow renderer's final material properties to the shadowIntegrated lighting material
            copyMaterialParams(lightToShadowRendererMap.get(l).getPostShadowMat(), mat, prefix); // !! This require a change to AbstractShadowRenderer, need to add a getter for this
            
        }
        
        return mat;
    }
    
     
 
    
    
    // take a geometery with Lighting.j3md and switch it to use ShadowIntegratedLighting.j3md
    private Material changeLightingToShadowedLighting(Geometry geom) {
        Material lightingMat = geom.getMaterial();
        Material mat = new Material(this.assetManager, PATH_TO_UPDATED_LIGHTING);
        
        copyMaterialParams(lightingMat, mat);
             
        //@TODO: copy over additional renderstate info here
        
        mat.setFloat("ShadowMapSize", DEFAULT_MAP_SIZE);
                
        
        // update the geometry's material
        geom.setMaterial(mat);
        
        return mat;
    }
    
    
    // copy over material params
    private void copyMaterialParams(Material from, Material to) {
        copyMaterialParams(from, to, "");
    }
    
    // copy over material params from one material to another
    private void copyMaterialParams(Material from, Material to, String prefix) {
       for (Iterator<MatParam> params = from.getParams().iterator(); params.hasNext();) {
            MatParam param = params.next();  
            if (to.getMaterialDef().getMaterialParam(prefix+param.getName()) == null) continue;
            to.setParam(prefix+param.getName(), param.getVarType(), param.getValue());
        } 
    }
    
    
    /* iteration throught all the shaodow receiving geometries to gather their materials
    * and change them to ShadowIntegratedLighting if required
    */ 
    private void processShadowReceivers() {

        
        for (int i = 0; i < sceneReceivers.size(); i++) {
            Material mat = sceneReceivers.get(i).getMaterial();

            if (shadowReceivingMaterials.contains(mat)) continue; // material already registered
            
            //checking if the material is Lighting.j3md
            if (mat.getMaterialDef().getAssetName() != null && mat.getMaterialDef().getAssetName().equals(LIGHTING_MAT_ASSET_NAME)) {
                Material newMaterial = changeLightingToShadowedLighting(sceneReceivers.get(i));
                shadowReceivingMaterials.add( newMaterial );

                copyMaterialParams(shadowRenderers.get(0).getPostShadowMat(), newMaterial);
            }

            // perhaps need to strip out materials that are no longer used here
        }
    }
   

    public void cleanup() {
    }
    
    public void reshape(ViewPort vp, int w, int h) {
    }
    
    public void postFrame(FrameBuffer out) {
    }
    
    
}
