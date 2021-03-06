
import { multiSelectTo as multiSelect } from './utils';

import memoizeOne from 'memoize-one';
import FileNode from "./FileNode.js";
import CompactFileNodeWrapper from './CompactFileNode/CompactFileNodeWrapper.js';

import { DragDropContext, Droppable } from 'react-beautiful-dnd';

import NewFolderIcon from "@material-ui/icons/CreateNewFolder";
import DeleteIcon from "@material-ui/icons/DeleteForever";
import DownloadButton from "@material-ui/icons/CloudDownload";
import LinkButton from "@material-ui/icons/Link";
import LogoutButton from "@material-ui/icons/ExitToApp";
import RefreshButton from "@material-ui/icons/Refresh";
import {listFiles} from "../../APICalls/APICalls";
import Button from '@material-ui/core/Button';

import {InputGroup, FormControl} from "react-bootstrap";
import TextField from '@material-ui/core/TextField';
import Dialog from '@material-ui/core/Dialog';
import DialogActions from '@material-ui/core/DialogActions';
import DialogContent from '@material-ui/core/DialogContent';
import DialogContentText from '@material-ui/core/DialogContentText';
import DialogTitle from '@material-ui/core/DialogTitle';
import SearchIcon from '@material-ui/icons/Search';

import InputBase from '@material-ui/core/InputBase';

import UploaderWrapper from "./UploaderWrapper.js";

import React, { Component } from 'react';

import {share, mkdir, deleteCall, download, getDownload, getSharableLink} from "../../APICalls/APICalls";

import { Breadcrumb, ButtonGroup, Button as BootStrapButton, OverlayTrigger, Tooltip } from 'react-bootstrap';
import {getFilesFromMemory, getIdsFromEndpoint, setFilesWithPathList, getPathFromMemory, 
		emptyFileNodesData, getEntities, setSelectedTasks, setSelectedTasksForSide,getSelectedTasks, getSelectedTasksFromSide, 
		unselectAll, getTaskFromId, makeFileNameFromPath, draggingTask, setFilesWithPathListAndId, getMapFromEndpoint} from "./initialize_dnd";

import {eventEmitter} from "../../App";

import {getType} from '../../constants.js';
import {DROPBOX_TYPE, GOOGLEDRIVE_TYPE, FTP_TYPE, SFTP_TYPE, GRIDFTP_TYPE, HTTP_TYPE, SCP_TYPE} from "../../constants";
import {CopyToClipboard} from 'react-copy-to-clipboard';

export default class EndpointBrowseComponent extends Component {

	constructor(props){
		super(props);
		this.state={
			route: "",
			directoryPath : getPathFromMemory(props.endpoint),
			ids: getIdsFromEndpoint(props.endpoint),
			openShare: false,
			shareUrl: "",
			openAFolder: false,
			addFolderName: "",
			searchText: "",
			ignoreCase : false,
			regex : false
		};

		this.getFilesFromBackend = this.getFilesFromBackend.bind(this);
		this.fileNodeDoubleClicked = this.fileNodeDoubleClicked.bind(this);
		this.getFilesFromBackendWithPath = this.getFilesFromBackendWithPath.bind(this);
		this.breadcrumbClicked = this.breadcrumbClicked.bind(this);
		this.fileNodeClicked = this.fileNodeClicked.bind(this);
		this.toggleSelection = this.toggleSelection.bind(this);
		this.toggleSelectionInGroup = this.toggleSelectionInGroup.bind(this);
		this.multiSelectTo = this.multiSelectTo.bind(this);
		this.onWindowTouchEnd = this.onWindowTouchEnd.bind(this);
		this.onWindowKeyDown = this.onWindowKeyDown.bind(this);
		this.onWindowClick = this.onWindowClick.bind(this);
		this.fileChangeHandler = this.fileChangeHandler.bind(this);
		this._handleAddFolderTextFieldChange = this._handleAddFolderTextFieldChange.bind(this);
		
		this.filenameAscendingOrderSort = this.filenameAscendingOrderSort.bind(this);
		this.sizeAscendingOrderSort = this.sizeAscendingOrderSort.bind(this);
		this.dateAscendingOrderSort = this.dateAscendingOrderSort.bind(this);
		this.permissionAscendingOrderSort = this.permissionAscendingOrderSort.bind(this);
		this.filenameDescendingOrderSort = this.filenameDescendingOrderSort.bind(this);
		this.sizeDescendingOrderSort = this.sizeDescendingOrderSort.bind(this);
		this.dateDescendingOrderSort = this.dateDescendingOrderSort.bind(this);
		this.permissionDescendingOrderSort = this.permissionDescendingOrderSort.bind(this);
		
		this.sortBy = this.sortBy.bind(this);
		
		if(this.state.directoryPath.length == 0)
			this.getFilesFromBackend(props.endpoint);
	}

	componentDidMount() {
	    window.addEventListener('click', this.onWindowClick);
	    window.addEventListener('keydown', this.onWindowKeyDown);
	    window.addEventListener('touchend', this.onWindowTouchEnd);
	    eventEmitter.on("fileChange", this.fileChangeHandler); 
	}

	fileChangeHandler(){
	    this.forceUpdate();
	}

	componentWillUnmount() {
	    window.removeEventListener('click', this.onWindowClick);
	    window.removeEventListener('keydown', this.onWindowKeyDown);
	    window.removeEventListener('touchend', this.onWindowTouchEnd);
	}
	

  	toggleSelection = (task) => {
  		const {endpoint} = this.props;
	    const selectedTaskIds = getSelectedTasksFromSide(endpoint);
	    const wasSelected: boolean = selectedTaskIds.includes(task);
	    const newTasks = (() => {
	      // Task was not previously selected
	      // now will be the only selected item
	      if (!wasSelected) {
	        return [task];
	      }
	      // Task was part of a selected group
	      // will now become the only selected item
	      if (selectedTaskIds.length > 1) {
	        return [task];
	      }
	      // task was previously selected but not in a group
	      // we will now clear the selection
	      return [];
	    })();
	    setSelectedTasksForSide(newTasks, endpoint);
  	};

  	toggleSelectionInGroup = (task) => {
  		const {endpoint} = this.props;
	    const selectedTasks = getSelectedTasksFromSide(endpoint);
	    const index: number = selectedTasks.indexOf(task);
	    // if not selected - add it to the selected items
	    if (index === -1) {
	      setSelectedTasksForSide([...selectedTasks, task], endpoint);
	      return;
	    }
	    // it was previously selected and now needs to be removed from the group
	    const shallow = [...selectedTasks];
	    shallow.splice(index, 1);

	    setSelectedTasksForSide(shallow, endpoint);
	};
	// This behaviour matches the MacOSX finder selection
	multiSelectTo = (newTask) => {
		const {endpoint} = this.props;
	    const updated = multiSelect(
	      getEntities()[endpoint.side],
	      getSelectedTasksFromSide(endpoint),
	      newTask
	 	);
	    if (updated == null) {
	      return;
	    }
	    setSelectedTasksForSide(updated, endpoint);
	};

	unselectAll = () => {
		unselectAll();
	};

	onWindowKeyDown = (event: KeyboardEvent) => {
	    if (event.defaultPrevented) {
	      return;
	    }

	    if (event.key === 'Escape') {
	      this.unselectAll();
	    }
	};

	onWindowClick = (		event: KeyboardEvent) => {
	    if (event.defaultPrevented) {
	      return;
	    }
	    //this.unselectAll();
	};

	onWindowTouchEnd = (event: TouchEvent) => {
	    if (event.defaultPrevented) {
	      return;
	    }
	};
	
	componentWillUnmount(){
		this.unselectAll();
	}

	fileNodeClicked(filename){
	}

	fileNodeDoubleClicked(filename, id){
		this.props.setLoading(true);
		this.getFilesFromBackendWithPath(this.props.endpoint, [...this.state.directoryPath, filename], [...this.state.ids, id]);
		this.unselectAll();
	}

	breadcrumbClicked(index){
		this.props.setLoading(true);
		this.state.directoryPath.length = index;
		this.state.ids.length = index+1;
		this.getFilesFromBackendWithPath(this.props.endpoint, this.state.directoryPath, this.state.ids);
	}

	getFilesFromBackend(endpoint){
		this.getFilesFromBackendWithPath(endpoint, [], [null]);
	}

	filenameAscendingOrderSort = (files) => {
		return files.sort((a, b) => { 
			if(a.dir && !b.dir){
				return -1;
			}else if(!a.dir && b.dir){
				return 1;
			}else{
				return a.name.localeCompare(b.name);
			}
		});
	}

	filenameDescendingOrderSort = (files) => {
		return files.sort((a, b) => { 
			if(a.dir && !b.dir){
				return -1;
			}else if(!a.dir && b.dir){
				return 1;
			}else{
				return b.name.localeCompare(a.name);
			}
		});
	}

	dateAscendingOrderSort(files){
		return files.sort((a, b) => { 
			return a.time - b.time;
		});
	}

	dateDescendingOrderSort(files){
		return files.sort((a, b) => { 
			return b.time - a.time;
		});
	}

	sizeAscendingOrderSort (files){
		return files.sort((a, b) => { 
			return a.size - b.size;
		});
	}

	sizeDescendingOrderSort(files){
		return files.sort((a, b) => { 
			return b.size - a.size;
		});
	}

	permissionAscendingOrderSort = (files) => {
		return files.sort((a, b) => { 
			if(a.perm && b.perm)
				return a.perm.localeCompare(b.perm);
			return 0;
		});
	}

	permissionDescendingOrderSort = (files) => {
		return files.sort((a, b) => { 
			if(a.perm && b.perm)
				return b.perm.localeCompare(a.perm);
			return 0;
		});
	}

	sortBy = (sortingFunc) => {
		const {endpoint} = this.props;
		const {directoryPath, ids} = this.state;
		let files = getFilesFromMemory(endpoint);
		let sortedfiles = sortingFunc(files);
		setFilesWithPathListAndId(sortedfiles, directoryPath, ids, endpoint);
		this.setState({directoryPath: directoryPath, ids: ids});
	}

	getFilesFromBackendWithPath(endpoint, path, id){
		var uri = endpoint.uri;
		const {setLoading} = this.props;
		setLoading(true);
		uri = makeFileNameFromPath(uri, path, "");

		listFiles(uri, endpoint, id[id.length-1], (data) =>{
			let sortedfiles = this.filenameAscendingOrderSort(data.files);
			setFilesWithPathListAndId(sortedfiles, path, id, endpoint);
			this.setState({directoryPath: path, ids: id});
			setLoading(false);
		}, (error) =>{
			this._handleError(error);
			setLoading(false);
		});
	};

	handleClickOpen = (url) => {
		this.setState({ openShare: true, shareUrl: url });
	};

	handleClickOpenAddFolder = () => {
		this.setState({ openAFolder: true });
	};

	handleClose = () => {
		this.setState({ openShare: false, openAFolder: false });
	};

	handleCloseWithFolderAdded = () =>{
		const {endpoint, setLoading} = this.props;
		const {directoryPath, addFolderName, ids} = this.state;
		this.setState({ openShare: false, openAFolder: false });
		let dirName = makeFileNameFromPath(endpoint.uri,directoryPath, addFolderName);
		const dirType = getType(endpoint);
		if(getType(endpoint) === GOOGLEDRIVE_TYPE){
			dirName = addFolderName;
		}
		//make api call
		mkdir(dirName,dirType, endpoint, (response) => {
			setLoading(true);
			this.getFilesFromBackendWithPath(endpoint, directoryPath, ids);
		}, (error) => {
			this._handleError(error);
		})
	}

	_handleError = (error) =>{
		eventEmitter.emit("errorOccured", error);
	}

	_handleConfirmation = (query) => {
		return window.confirm(query);
	}

	_handleAddFolderTextFieldChange = (e) => {
        this.setState({
            addFolderName: e.target.value
        });
    }

    handleCloseWithFileDeleted = (files) => {
    	const {endpoint, setLoading} = this.props;
    	const {directoryPath, ids} = this.state;
    	const len = files.length;
    	var i = 0;
    	if(this._handleConfirmation("Are you sure you want to delete" + files.reduce((a, v) => a+"\n"+v.name, ""))){
    		setLoading(true);
    		files.map((file) => {
    			const fileName = makeFileNameFromPath(endpoint.uri, directoryPath, file.name);
    			
				console.log("delete before success", directoryPath, ids)
    			deleteCall( fileName, endpoint,  file.id, (response) => {
    				console.log("delete after success", directoryPath, ids)
    				i++;
    				if(i == len){
    					this.getFilesFromBackendWithPath(endpoint, directoryPath, ids);
    				}
    			}, (error) => {
    				this._handleError(error);
    			});
    		});
    	}
    }

	

	render(){
		const {endpoint, back, setLoading, getLoading, displayStyle} = this.props;
		const {directoryPath, displayMode, searchText, compactStylePos} = this.state;
		

		const list = getFilesFromMemory(endpoint) || [];
		let displayList = Object.keys(list);


		if(searchText.length > 0){
			if(this.state.regex){
				var flags = this.state.ignoreCase? "i" : "";
				try{
					var regex = new RegExp(searchText, flags);
					displayList = Object.keys(list).filter(key => regex.test(list[key].name));
				} catch {
					console.log("Invalid regex")
				}	
			}
			else{
				if(this.state.ignoreCase){
					var keyword = searchText.toLowerCase()
					displayList = Object.keys(list).filter(key => list[key].name.toLowerCase().includes(keyword));
				}
				else
					displayList = Object.keys(list).filter(key => list[key].name.includes(searchText));
			}
		} 
		

		const iconStyle = {fontSize: "15px", width: "100%"};
		const buttonStyle = {flexGrow: 1, padding: "5px"};
		const buttonGroupStyle = {display: "flex", flexDirection: "row", flexGrow: 2};

		const selectedTasks = getSelectedTasksFromSide(endpoint);
		const loading = getLoading();
		const tooltip = (name) => (
		  <Tooltip id="tooltip">
		  	{name}
		  </Tooltip>
		);


		return (
		<div style={{display: "flex", flexDirection: "column",  minHeight: "100%", maxHeight: "400px", }}>
	        <Dialog
	          open={this.state.openShare}
	          onClose={this.handleClose}
						aria-labelledby="form-dialog-title"
	        >
	          <DialogTitle id="form-dialog-title">Share</DialogTitle>
	          <DialogContent style={{width:"100%"}}>
	            <DialogContentText>
	              Share this URL to allow others access to the selected file:
	            </DialogContentText>
	            <div style={{width:"96%", float:"left"}}><TextField
								autoFocus
								id="name"
								disabled
								value={this.state.shareUrl}
								fullWidth
	            ></TextField></div>
							<CopyToClipboard text = {this.state.shareUrl} style={{float:"right", width:"3%"}}>
							<svg width="24" height="24" viewBox="0 0 24 24">
								<path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/>
							</svg>								
							</CopyToClipboard>
	          </DialogContent>
	          <DialogActions>
	            <Button onClick={this.handleClose} color="primary">
	              Close
	            </Button>
	          </DialogActions>
	        </Dialog>

	        <Dialog
	          open={this.state.openAFolder}
	          onClose={this.handleClose}
	          aria-labelledby="form-dialog-title"
	        >
	          <DialogTitle id="form-dialog-title">Add folder</DialogTitle>
	          <DialogContent>
	            <TextField
	              autoFocus
	              id="name"
	              label="name"
	              onChange={this._handleAddFolderTextFieldChange}
	              fullWidth
	            />
	          </DialogContent>
	          <DialogActions>
	            <Button onClick={this.handleCloseWithFolderAdded} color="primary">
	              Add
	            </Button>
	          </DialogActions>
	        </Dialog>
			<div style={{display: "flex",alighSelf: "stretch", height: "60px", backgroundColor: "#d9edf7", width: "100%", overflowX: "scroll", overflowY: "hidden"}}>
				<Breadcrumb style={{  float: "left", backgroundColor: "#d9edf7", whiteSpace:"nowrap"}}>
				  <Breadcrumb.Item key={endpoint.uri} style={{display: "inline-block"}}><Button style={{padding: "0px", margin: "0px"}} onClick={() => this.breadcrumbClicked(0)}>{endpoint.uri}</Button></Breadcrumb.Item>
				  {directoryPath.map((item, index) => <Breadcrumb.Item key={item+index} style={{display: "inline-block"}}><Button style={{padding: "0px", margin: "0px"}} onClick={() => this.breadcrumbClicked(index+1)}>{item}</Button></Breadcrumb.Item>)}
				</Breadcrumb>
			</div>
			
			<div style={{alignSelf: "stretch", display: "flex", flexDirection: "row", alignItems: "center", height: "40px", padding: "10px", backgroundColor: "#d9edf7"}}>
				<ButtonGroup style={buttonGroupStyle}>
				  <OverlayTrigger placement="top" overlay={tooltip("New Folder")}>
				  	<BootStrapButton style={buttonStyle} onClick={() => {
				  		this.handleClickOpenAddFolder()
				  	}}>
				  		<NewFolderIcon style={iconStyle}/>
				  	</BootStrapButton>
				  </OverlayTrigger>

				  <OverlayTrigger placement="top" 
						overlay={tooltip("Upload")}>
						<UploaderWrapper endpoint={endpoint} directoryPath={directoryPath} lastestId={this.state.ids[this.state.ids.length-1]}/>
				  </OverlayTrigger>
				  <OverlayTrigger placement="top" 
				  	overlay={tooltip("Delete")}>
				  	<BootStrapButton disabled={getSelectedTasksFromSide(endpoint).length < 1} onClick={() => {
				  		this.handleCloseWithFileDeleted(getSelectedTasksFromSide(endpoint));
				  	}}
				  	style={buttonStyle}><DeleteIcon style={iconStyle}/></BootStrapButton>
				  </OverlayTrigger>
					  	<OverlayTrigger placement="top" overlay={tooltip("Download")}>
					  		<BootStrapButton disabled={getSelectedTasksFromSide(endpoint).length != 1 || getSelectedTasksFromSide(endpoint)[0].dir} 
					  		onClick={() => {
					  			const downloadUrl = makeFileNameFromPath(endpoint.uri,directoryPath, getSelectedTasksFromSide(endpoint)[0].name);
									const taskList = getSelectedTasksFromSide(endpoint);
									if(getType(endpoint) === SFTP_TYPE || getType(endpoint) == SCP_TYPE){
										getDownload(downloadUrl, endpoint.credential, taskList);
									}
									else if(getType(endpoint) == HTTP_TYPE){
										window.open(downloadUrl);
									}
									else{
						  			download(downloadUrl, endpoint.credential, taskList[0].id)
						  		}
					  		}}
					  		style={buttonStyle}><DownloadButton style={iconStyle}/></BootStrapButton>
						</OverlayTrigger>
					<OverlayTrigger placement="top"  overlay={tooltip("Share")}>
						<BootStrapButton disabled = {getSelectedTasksFromSide(endpoint).length != 1 || getSelectedTasksFromSide(endpoint)[0].dir
						|| !(getType(endpoint) === GOOGLEDRIVE_TYPE || getType(endpoint) === DROPBOX_TYPE)} style={buttonStyle} onClick={() => {
							const downloadUrl = makeFileNameFromPath(endpoint.uri,directoryPath, getSelectedTasksFromSide(endpoint)[0].name);
							const taskList = getSelectedTasksFromSide(endpoint);
							getSharableLink(downloadUrl, endpoint.credential, taskList[0].id)
							.then(response => {
								if(response !== ""){
									this.handleClickOpen(response);
								}
								else{
									eventEmitter.emit("errorOccured", "Error encountered while generating link");
								}	
							})
				  		}}>
				  			<LinkButton style={iconStyle}/>
				  		</BootStrapButton>
					</OverlayTrigger>
					<OverlayTrigger placement="top" overlay={tooltip("Refresh")}>
				  		<BootStrapButton style={buttonStyle}  onClick={() => {
				  			setLoading(true);
				  			this.getFilesFromBackendWithPath(endpoint, directoryPath, this.state.ids);
				  		}}>
				  			<RefreshButton style={iconStyle}/>
				  		</BootStrapButton>
					</OverlayTrigger>
					<OverlayTrigger placement="top" overlay={tooltip("Log out")}>
				  		<BootStrapButton bsStyle="primary" style={buttonStyle} onClick={() =>
				  		{
				  			emptyFileNodesData(endpoint);
				  			this.unselectAll();
				  			back();
				  		}}
				  			><LogoutButton style={iconStyle}/></BootStrapButton>
					</OverlayTrigger>
				</ButtonGroup>
			</div>

			<div style={{alignSelf: "stretch", display: "flex", flexDirection: "row", alignItems: "center", height: "40px", padding: "10px", backgroundColor: "#d9edf7"}}>
				<InputGroup style={{padding: "4px",marginLeft: 4, flex: 1, background: "#d9edf7", borderRadius: "5px"}}>
					<FormControl placeholder="Search"
						onChange={(event) => {
							this.setState({searchText: event.target.value})
						}}/>
					<InputGroup.Button>	
					<OverlayTrigger placement="top" overlay={tooltip("Ignore Case")}>
						<Button id="ignoreCase" style={{backgroundColor : "white", color: this.state.ignoreCase ? "white" : "black", backgroundColor: this.state.ignoreCase ? "#337AB6" : "white" ,
						 border: "1px solid #ccc",fontFamily : "Arial", textTransform: "capitalize", fontFamily : "monospace", fontSize : "10px", minWidth : "17px"}} 
						onClick={() => {
							this.setState({ignoreCase : !this.state.ignoreCase})
							}
						}>Aa</Button>
					</OverlayTrigger>
					<OverlayTrigger placement="top" overlay={tooltip("Regular Expression")}>
						<Button id="regex" style={{backgroundColor : "white", color: this.state.regex ? "white" : "black", backgroundColor: this.state.regex ? "#337AB6" : "white" ,
						 border: "1px solid #ccc", fontSize : "10px", minWidth : "17px"}}
						 onClick={() => {
							this.setState({regex : !this.state.regex})
							}
						}><b>*.</b></Button>
					</OverlayTrigger>
					</InputGroup.Button>
				</InputGroup>
			</div>

			
			<Droppable droppableId={endpoint.side} > 
				{(provided: DroppableProvided, snapshot: DroppableStateSnapshot) => (
					<div
						ref={provided.innerRef}
						{...provided.droppableProps}
						style={{  overflowY: 'scroll', width: "100%", marginTop: "0px", height: "320px"}}
					>
						{!loading && Object.keys(list).length == 0 &&
							<h2>
								This directory is EMPTY
							</h2>
						}

						{loading && Object.keys(list).length == 0 &&
							<h2>
								LOADING
							</h2>
						}

						{!loading && displayList.length == 0 && Object.keys(list).length > 0 &&
							<h2>
								No Search Result
							</h2>
						}

						{displayStyle == "compact" && !loading && displayList.length != 0 &&
							<CompactFileNodeWrapper 
								sortFunctions = {[{"Asc": this.filenameAscendingOrderSort, "Desc" : this.filenameDescendingOrderSort}, 
												  {"Asc": this.dateAscendingOrderSort, "Desc":this.dateDescendingOrderSort},
												  {"Asc": this.permissionAscendingOrderSort, "Desc":this.permissionDescendingOrderSort},
												  {"Asc": this.sizeAscendingOrderSort, "Desc":this.sizeDescendingOrderSort}]}
								sortBy = {this.sortBy}
								list={list} 
								displayList={displayList} 
								selectedTasks={selectedTasks} 
								endpoint={endpoint} 
								draggingTask={draggingTask}
								toggleSelection={this.toggleSelection}
								onClick={this.fileNodeClicked}
								onDoubleClick={this.fileNodeDoubleClicked}
								toggleSelectionInGroup={this.toggleSelectionInGroup}
					            multiSelectTo={this.multiSelectTo}
							/>
						}


						{displayStyle == "comfort" && displayList.map((fileId, index) => {
							const file = list[fileId];
							const isSelected: boolean = Boolean(
			                  selectedTasks.indexOf(file)!=-1,
			                );
			                const isGhosting: boolean =
			                  isSelected &&
			                  Boolean(draggingTask) &&
			                  draggingTask.name !== file.name;

							  return(
								<FileNode
									key={fileId}
									index={index}
									file={file}
									selectionCount={selectedTasks.length}
									onClick={this.fileNodeClicked}
									onDoubleClick={this.fileNodeDoubleClicked}
									side={endpoint.side}
									isSelected={isSelected}
									endpoint={endpoint}
				                    isGhosting={isGhosting}
				                    toggleSelection={this.toggleSelection}
				                    toggleSelectionInGroup={this.toggleSelectionInGroup}
				                    multiSelectTo={this.multiSelectTo}
							/>);
						})}
						{provided.placeHolder}
					</div>
				)}
			</Droppable>
		</div>);
	}
}


